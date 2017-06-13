/*
 *  Copyright (C) 2014 Scrive AB
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import org.bouncycastle.util.encoders.Base64;
import org.json.JSONException;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.itextpdf.text.BadElementException;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.CMYKColor;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfAction;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfDestination;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPTableEvent;
import com.itextpdf.text.pdf.PdfPageEvent;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;

class FileAttachment
{
    public byte[] content;
    public String name;

    public FileAttachment(String filename, String name)
        throws IOException
    {
        RandomAccessFile f = new RandomAccessFile(filename, "r");
        this.content = new byte[(int)f.length()];
        f.read(this.content);
        this.name = name;
        f.close();
    }

    public FileAttachment(byte[] content, String name) {
        this.content = content;
        this.name = name;
    }
}

/*
 * This class is needed to draw frame around a table in PDF. We draw
 * frames around each table part after the table is split between
 * pages.
 *
 * Sadly there is no such thing as 'table border' property. If it were
 * we would need not do such a kludge.
 */
class PdfPTableDrawFrameAroundTable implements PdfPTableEvent
{
    public void tableLayout(PdfPTable table, float[][] widths, float[] heights, int headerRows, int rowStart, PdfContentByte[] canvases)
    {
        PdfContentByte lineCanvas = canvases[PdfPTable.LINECANVAS];
        Rectangle frame = new Rectangle(widths[0][0],
                                        heights[0],
                                        widths[0][widths[0].length-1],
                                        heights[heights.length-1]);
        frame.setBorderColor(AddVerificationPages.frameColor);
        frame.setBorder(15);
        frame.setBorderWidth(1);
        lineCanvas.rectangle(frame);
    }
}


class Base64DecodeException extends IOException
{
    private static final long serialVersionUID = -4863901814975446459L;
}

/*

Sealing works like this:

1. Open seal spec file (json/yaml) given as argument on the command line.
2. Open input file (if any).
3. On pages of input file add fields at exact positions.
4. Save it to byte stream.
5. Create final seal pages (if preseal == False).
6. Concatenate 4 and 5.
7. Save to output.

*/


public class AddVerificationPages extends Engine {

    /*
     * Define some colors.
     */

    static CMYKColor darkTextColor = new CMYKColor(0.806f, 0.719f, 0.51f, 0.504f);
    static CMYKColor lightTextColor = new CMYKColor(0.597f, 0.512f, 0.508f, 0.201f);
    static CMYKColor frameColor = new CMYKColor(0f, 0f, 0f, 0.333f);

	static PdfName scriveTag = new PdfName("Scrive:Tag");
	static PdfName scriveTagBackground = new PdfName("Scrive:Background");
	static PdfName scriveTagVerPage = new PdfName("Scrive:VerificationPage");
	static PdfName scriveTagFooter = new PdfName("Scrive:Footer");

    SealSpec spec = null;
    
    public void Init(InputStream specFile, String inputOverride, String outputOverride) throws IOException {
        try {
            spec = SealSpec.FromJSON(specFile);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        if( inputOverride!=null ) {
            spec.input = inputOverride;
        }
        if( outputOverride!=null ) {
            spec.output = outputOverride;
        }
    }

    /*
     * Concatenate all documents, page by page, output to OutputStream
     *
     * I did not find a way to do this without directly serializing
     * document.
     */
    public static void concatenatePdfsInto(Iterable<PdfReader> sources, OutputStream os)
        throws IOException, DocumentException
    {
        Document document = new Document();
        PdfCopy writer = new PdfCopy(document, os);
        document.open();

        Float height = null;
        for(PdfReader reader: sources) {
            if (height == null) {
                height = reader.getPageSize(1).getHeight();
            }
            int count = reader.getNumberOfPages();
            for( int i=1; i<=count; i++ ) {
                PdfImportedPage page = writer.getImportedPage(reader, i);
                writer.addPage(page);
            }
            writer.freeReader(reader);
            reader.close();
        }

        PdfDestination pdfDest = new PdfDestination(PdfDestination.XYZ, 0, height, 1f);
        PdfAction action = PdfAction.gotoLocalPage(1, pdfDest, writer);
        writer.setOpenAction(action);

        document.close();
        writer.close();
    }

    /*
     * Put footer with attachment name on each page.
     */
    public static ByteArrayOutputStream addAttachmentFooter(SealSpec spec, FileDesc file, PdfReader reader)
        throws DocumentException, IOException
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PdfStamper stamper = new PdfStamper(reader, os);

        stamper.setFormFlattening(true);
        stamper.setFreeTextFlattening(true);

        int count = reader.getNumberOfPages();
        for( int i=1; i<=count; i++ ) {

            Rectangle cropBox = reader.getCropBox(i);
            int rotate = reader.getPageRotation(i);
            while (rotate > 0) {
                cropBox = cropBox.rotate();
                rotate -= 90;
            }

            PdfContentByte canvas = stamper.getOverContent(i);
            if(!spec.preseal) {
                addPaginationFooter(spec, stamper, canvas, cropBox,
                        spec.documentNumberText, file.role);
            }
        }
        stamper.close();
        reader.close();
        return os;
    }

    /*
     * Create a PDF with one page with a centered image
     */
    public static ByteArrayOutputStream pdfFromImageFile(String filepath)
        throws DocumentException, IOException
    {
        int rotationDegrees = 0;
        boolean flip = false;
        try {
            File jpegFile = new File(filepath);
            Metadata metadata = ImageMetadataReader.readMetadata(jpegFile);
            /*
            for (Directory directory : metadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    System.out.println(tag);
                }
            }
            */
            ExifIFD0Directory exifIFD0 = metadata.getDirectory(ExifIFD0Directory.class);
            if( exifIFD0!=null ) {
                int orientation = exifIFD0.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                switch( orientation ) {
                case 1:
                    rotationDegrees = 0;
                    flip = false;
                    break;
                case 2:
                    rotationDegrees = 0;
                    flip = true;
                    break;
                case 3:
                    rotationDegrees = 180;
                    flip = false;
                    break;
                case 4:
                    rotationDegrees = 180;
                    flip = true;
                    break;
                case 5:
                    rotationDegrees = 270;
                    flip = true;
                    break;
                case 6:
                    rotationDegrees = 270;
                    flip = false;
                    break;
                case 7:
                    rotationDegrees = 90;
                    flip = true;
                    break;
                case 8:
                    rotationDegrees = 90;
                    flip = false;
                    break;
                }
            }
        }
        catch(ImageProcessingException e) {
            // could not parse metadata
        }
        catch(MetadataException e) {
            // orientation not found
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Image image = Image.getInstance(filepath);
        Document document = new Document();
        PdfWriter writer = PdfWriter.getInstance(document, os);

        document.open();
        document.newPage();
        float pageWidth = document.right() - document.left();
        float pageHeight = document.top() - document.bottom();
        float scaleWidth = (document.right() - document.left()) / image.getScaledWidth();
        float scaleHeight = (document.top() - document.bottom()) / image.getScaledHeight();
        // The default 72 DPI yields a bit too large and grainy
        // images, so pretend we have ... 113 DPI.

        // Why this number?  The problem is image files with
        // screenshots that don't have any resolution information
        // (DPI), yet, some people may have expectations that if one
        // puts a screenshot in a PDF and views the PDF on the screen
        // when zoomed to 100%, the screenshot image should have the
        // same size as the original screenshot.  In an unscientific
        // test, the only platform that renders a PDF page in true
        // physical size when zoomed to 100% is OS X.  So we picked a
        // DPI that happens to fit well with Macbook Pros 13" which
        // have 113.48 pixels per inch (assuming pixel doubling with
        // Retina displays).

        // TODO: We may want to check if the image resolution is
        // different from the default of 72 DPI, which indicates that
        // the image actually knows it geometry and we may want to
        // respect that.

        float dpiFactor = 72f / 113f;
        float scaleFactor = Math.min(scaleWidth, Math.min(scaleHeight,dpiFactor));
        image.setRotationDegrees(rotationDegrees);

        image.scalePercent(scaleFactor * 100);
        image.setAbsolutePosition(document.left() + (pageWidth - image.getScaledWidth())/2,
                                  document.top() - image.getScaledHeight());
        document.add(image);
        document.close();
        return os;
    }

    /*
     * Process each page of a source document and put all fields on
     * top of it.  If sealing add also paginatin markers.
     */
    public static void stampFieldsAndPaginationOverPdf(SealSpec spec, PdfReader reader, ArrayList<Field> fields, ArrayList<HighlightedImage> highlightedImages, OutputStream os)
        throws DocumentException, IOException, Base64DecodeException
    {
        PdfStamper stamper = new PdfStamper(reader, os);
        PdfReader background = null;
        if( spec.background != null ) {
            background = new PdfReader(spec.background);
        }

        PdfReader sealMarker = getSealMarker();
        PdfImportedPage sealMarkerImported = stamper.getImportedPage(sealMarker, 1);

        int count = reader.getNumberOfPages();
        for( int i=1; i<=count; i++ ) {

            Rectangle cropBox = reader.getCropBox(i);
            int rotate = reader.getPageRotation(i);
            while (rotate > 0) {
                cropBox = cropBox.rotate();
                rotate -= 90;
            }
            if( background!=null ) {
                PdfImportedPage backgroundImported = stamper.getImportedPage(background, i);
                if( backgroundImported!=null ) {
                	PdfContentByte canvas = stamper.getUnderContent(i);
                    canvas.beginMarkedContentSequence(scriveTagBackground);
                    canvas.addTemplate(backgroundImported, 0, 0);
                    canvas.endMarkedContentSequence();
                }
            }

            PdfContentByte overCanvas = stamper.getOverContent(i); // There's also getUnderContent() if we need to stamp behind text
            for( Field field : fields ) {
                if(field.page==i && !field.onlyForSummary) {

                    if( field.value != null ) {
                        float fs = field.fontSize * cropBox.getWidth();

                        if( fs<=0 )
                            fs = 10f;

                        BaseColor color;
                        if( !field.greyed ) {
                            color = new CMYKColor(0,0,0,1f);
                        }
                        else {
                            color = new CMYKColor(0,0,0,127);
                        }

                        Paragraph para = createParagraph(field.value, fs, Font.NORMAL, color);

                        /*
                         * fontOffset used to compensate for text
                         * position that was different in browser
                         * compared to what was in PDF. It does not
                         * seems to be necessary anymore.
                         *
                         * Note: fontBaseline is set as side effect of
                         * createParagraph. Multiple results in Java.
                         */
                        float fontOffset   = 0 * fs;

                        float realx = field.x * cropBox.getWidth() + cropBox.getLeft() - fontOffset;
                        float realy = (1 - field.y) * cropBox.getHeight() + cropBox.getBottom() - fontBaseline;

                        ColumnText.showTextAligned(overCanvas, Element.ALIGN_LEFT,
                                                   para,
                                                   realx, realy,
                                                   0);
                    }
                    else if( field.valueBase64 !=null ) {

                        float absoluteWidth = field.image_w * cropBox.getWidth();
                        float absoluteHeight = absoluteWidth; // Square - same width and height

                        if( field.image_h != 0.0) { // Unless height is provided
                          absoluteHeight = field.image_h * cropBox.getHeight();
                        }

                        float realx = field.x * cropBox.getWidth() + cropBox.getLeft();
                        float realy = (1 - field.y) * cropBox.getHeight() + cropBox.getBottom() - absoluteHeight;

                        Image image = createImageWithKeyColor(field.valueBase64);

                        float initialRotation = image.getInitialRotation();
                        boolean flip_xy = false;
                        if( Math.abs(initialRotation - Math.PI/2)<0.01 ||
                            Math.abs(initialRotation - 3*Math.PI/2)<0.01 ) {
                            flip_xy = true;
                        }

                        image.setAbsolutePosition(realx,realy);
                        image.scaleAbsolute(flip_xy ? absoluteHeight : absoluteWidth,
                                            flip_xy ? absoluteWidth : absoluteHeight);

                        overCanvas.addImage(image);
                    }
                }
            }

            for( HighlightedImage hp : highlightedImages ) {
                if(hp.page==i && hp.imageBase64 != null) {

                    float realx = cropBox.getLeft();
                    float realy = cropBox.getBottom();

                    Image image = createImageWithKeyColor(hp.imageBase64);

                    float initialRotation = image.getInitialRotation();
                    boolean flip_xy = false;
                    if( Math.abs(initialRotation - Math.PI/2)<0.01 ||
                        Math.abs(initialRotation - 3*Math.PI/2)<0.01 ) {
                        flip_xy = true;
                    }


                    float absoluteWidth = cropBox.getWidth();
                    float absoluteHeight = cropBox.getHeight();
                    image.setAbsolutePosition(realx,realy);
                    image.scaleAbsolute(flip_xy ? absoluteHeight : absoluteWidth,
                                        flip_xy ? absoluteWidth : absoluteHeight);

                    overCanvas.addImage(image);
                }
            }




            if(!spec.preseal && !spec.disableFooter) {
                addPaginationFooter(spec, stamper, overCanvas, cropBox,
                        spec.documentNumberText, spec.initialsText);
            }
        }
        stamper.close();
        reader.close();
    }

    /*
     * Add pagination at the bottom of the page, only use if not presealing.
     */
    public static void addPaginationFooter(SealSpec spec, PdfStamper stamper, PdfContentByte canvas, Rectangle cropBox, String lefttext, String righttext)
        throws DocumentException, IOException
    {
        PdfReader sealMarker = getSealMarker();
        PdfImportedPage sealMarkerImported = stamper.getImportedPage(sealMarker, 1);

        canvas.beginMarkedContentSequence(scriveTagFooter);

        float requestedSealSize = 18f;
        canvas.addTemplate(sealMarkerImported,
                requestedSealSize/sealMarkerImported.getWidth(),
                0, 0,
                requestedSealSize/sealMarkerImported.getHeight(),
                cropBox.getLeft() + cropBox.getWidth()/2 - requestedSealSize/2,
                cropBox.getBottom() + 23 - requestedSealSize/2);

        Paragraph para = createParagraph(lefttext, 8, Font.NORMAL, lightTextColor);

        ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                para,
                cropBox.getLeft() + cropBox.getWidth()/2 - requestedSealSize,
                20,
                0);
        float docnrtextwidth = ColumnText.getWidth(para);

        para = createParagraph(righttext, 8, Font.NORMAL, lightTextColor);
        ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                para,
                cropBox.getLeft() + cropBox.getWidth()/2 + requestedSealSize,
                20,
                0);
        float signedinitialswidth = ColumnText.getWidth(para);

        /*
         * This is the blue line at the bottom color
         */
         CMYKColor color = new CMYKColor(0.8f, 0.6f, 0.3f, 0.4f);
        Rectangle rect;
        rect = new Rectangle(cropBox.getLeft() + 60,
                23,
                cropBox.getLeft() + cropBox.getWidth()/2 - requestedSealSize - docnrtextwidth - requestedSealSize/2,
                23);
        rect.setBorderWidth(1);
        rect.setBorderColor(color);
        rect.setBorder(Rectangle.BOTTOM);
        canvas.rectangle(rect);

        rect = new Rectangle(cropBox.getRight() - 60,
                23,
                cropBox.getLeft() + cropBox.getWidth()/2 + requestedSealSize + signedinitialswidth + requestedSealSize/2,
                23);
        rect.setBorderWidth(1);
        rect.setBorderColor(color);
        rect.setBorder(Rectangle.BOTTOM);
        canvas.rectangle(rect);
        canvas.endMarkedContentSequence();        
    }


    /*
     * Gather all fields from all places they could be in SealSpec.
     */
    public static ArrayList<Field> getAllFields(SealSpec spec)
    {
        ArrayList<Field> result = new ArrayList<Field>();
        for(Person person : spec.persons) {
            result.addAll(person.fields);
        }
        for(Person person : spec.secretaries) {
            result.addAll(person.fields);
        }
        result.addAll(spec.fields);
        return result;
    }

    /*
     * Gather all highlightImages from all signatories
     */
    public static ArrayList<HighlightedImage> getAllHighlightedImage(SealSpec spec)
    {
        ArrayList<HighlightedImage> result = new ArrayList<HighlightedImage>();
        for(Person person : spec.persons) {
            result.addAll(person.highlightedImages);
        }
        return result;
    }

    static Image createImageWithKeyColor(String valueBase64) throws Base64DecodeException, BadElementException, MalformedURLException, IOException, DocumentException
    {
        byte rawdata[] = Base64.decode(valueBase64);
        if( rawdata==null ) {
            throw new Base64DecodeException();
        }

        int rotationDegrees = 0;
        boolean flip = false;
        try {
            InputStream inputStream = new ByteArrayInputStream(rawdata);
            BufferedInputStream bufferedInutStream = new BufferedInputStream(inputStream);
            Metadata metadata = ImageMetadataReader.readMetadata(bufferedInutStream, false);
            /*
            for (Directory directory : metadata.getDirectories()) {
                for (Tag tag : directory.getTags()) {
                    System.out.println(tag);
                }
            }
            */
            ExifIFD0Directory exifIFD0 = metadata.getDirectory(ExifIFD0Directory.class);
            if( exifIFD0!=null ) {
                int orientation = exifIFD0.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                switch( orientation ) {
                case 1:
                    rotationDegrees = 0;
                    flip = false;
                    break;
                case 2:
                    rotationDegrees = 0;
                    flip = true;
                    break;
                case 3:
                    rotationDegrees = 180;
                    flip = false;
                    break;
                case 4:
                    rotationDegrees = 180;
                    flip = true;
                    break;
                case 5:
                    rotationDegrees = 270;
                    flip = true;
                    break;
                case 6:
                    rotationDegrees = 270;
                    flip = false;
                    break;
                case 7:
                    rotationDegrees = 90;
                    flip = true;
                    break;
                case 8:
                    rotationDegrees = 90;
                    flip = false;
                    break;
                }
            }

        }
        catch(ImageProcessingException e) {
            // could not parse metadata
        }
        catch(MetadataException e) {
            // orientation not found
        }

        BufferedImage bufImg = ImageIO.read(new ByteArrayInputStream(rawdata));

        /*
        System.err.println("getTransparency: " + bufImg.getTransparency());
        System.err.println("isAlphaPremultiplied: " + bufImg.isAlphaPremultiplied());
        System.err.println("getType: " + bufImg.getType());
        SampleModel sampleModel = bufImg.getSampleModel();
        System.err.println("getSampleModel.getDataType: " + sampleModel.getDataType());
        System.err.println("getSampleModel.getNumBands: " + sampleModel.getNumBands());
        System.err.println("getSampleModel.getNumDataElements: " + sampleModel.getNumDataElements());
        */

        BufferedImage bufImg2 = new BufferedImage(bufImg.getWidth(), bufImg.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
        for (int y=0; y<bufImg.getHeight(); y++ ) {
            for (int x=0; x<bufImg.getWidth(); x++ ) {
                int argb = bufImg.getRGB(x,y);
                int r = (argb >>> 16) & 255;
                int g = (argb >>> 8) & 255;
                int b = (argb >>> 0) & 255;
                int a = (argb >>> 24) & 255;
                int min_color = Math.min(r, Math.min(g,b));
                //min_color = 0;
                /*
                 * In a sense we would like to create an identity
                 * transformation from RGB to ARGB knowing that alpha
                 * will blend with WHITE. Intuitivelly we would like
                 * to take as much white from RGB and give it to
                 * ARGB. We can do that by moving as much white from
                 * RGB to A.
                 *
                 * In the degenerate case of all colors being the same
                 * we can set RGB to 0,0,0 and move all colors into A.
                 *
                 * c' - color in source file and expected on screen after alpha rendering
                 * c  - color to put in output file
                 * c' = a*c*255 + (255-a)*255
                 * a*c = c'*255 - (255-a)*255
                 * c = (c' - (255-a))*255/a
                 *
                 * After successful transformation lowest component
                 * should have value 0. Distance between 255 and that
                 * component should be expanded, all other distances
                 * should be expanded proportionally.
                 *
                 * Note: java.image support alpha channel
                 * premultiplied, but here we try to blend not with
                 * zero but with full white color. I did not find a
                 * method to do this corectly using build in methods.
                 */
                int a2 = 255 - min_color;
                if( a2!=0 ) {
                    r = (r - (255-a2))*255/a2;
                    g = (g - (255-a2))*255/a2;
                    b = (b - (255-a2))*255/a2;
                }
                a = a*a2/255;
                int nargb = (a << 24) | (r << 16) | (g << 8) | (b << 0);
                bufImg2.setRGB(x,y,nargb);
            }
        }
        /*
         * Here we do not use preblended (alpha premultiplied) because
         * PDF could use this when /Matte key is present in image
         * dictionary. This problematic so just use non-preblended
         * mode here.
         */

        Image image = Image.getInstance(bufImg2, null);
        image.setInitialRotation((float)(rotationDegrees*Math.PI/180));
        return image;
    }

    /*
     * Helper function that creates a box for each person involved in a
     * document signing process.
     */
    public static void addPersonsTable(Iterable<Person> persons, Document document, SealSpec spec)
        throws DocumentException, IOException, Base64DecodeException
    {
        Paragraph para;

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100f);
        table.setWidths(new int[]{1, 1});

        int cells = 0;
        for( Person person: persons ) {
            PdfPCell cell;
            cell = new PdfPCell();
            cell.setBorderColor(frameColor);
            cell.setPadding(15);
            cell.setPaddingTop(5);
            cell.setPaddingBottom(12);
            cell.setBorderWidth(1f);

            if(!person.fullname.equals("")) {
                para = createParagraph(person.fullname, 10,
                                       person.fullnameverified ? Font.BOLDITALIC : Font.BOLD,
                                       lightTextColor );
                para.setLeading(0f, 1.2f);
                cell.addElement(para);
            }
            if(!person.company.equals("")) {
                para = createParagraph(person.company, 10,
                                       person.companyverified ? Font.ITALIC : Font.NORMAL,
                                       lightTextColor);
                para.setLeading(0f, 1.2f);
                cell.addElement(para);
            }
            /*
             * Spacing.
             */
            cell.addElement(new Paragraph(""));
            String personalNumberText = person.personalNumberText;
            if(!personalNumberText.equals("")) {
                para = createParagraph(personalNumberText, 10,
                                       person.numberverified ? Font.ITALIC : Font.NORMAL,
                                       lightTextColor );
                para.setLeading(0f, 1.2f);
                cell.addElement(para);
            }
            String companyNumberText = person.companyNumberText;
            if(!companyNumberText.equals("")) {
                para = createParagraph(companyNumberText, 10,
                                       person.companyverified ? Font.ITALIC : Font.NORMAL,
                                       lightTextColor);
                para.setLeading(0f, 1.2f);
                cell.addElement(para);
            }
            if(!person.email.equals("")) {
                para = createParagraph(person.email, 10,
                                       person.emailverified ? Font.ITALIC : Font.NORMAL,
                                       lightTextColor);
                para.setLeading(0f, 1.2f);
                cell.addElement(para);
            }
            if(!person.phone.equals("")) {
                para = createParagraph(person.phone, 10,
                                       person.phoneverified ? Font.ITALIC : Font.NORMAL,
                                       lightTextColor);
                para.setLeading(0f, 1.2f);
                cell.addElement(para);
            }
            for( Field field : person.fields ) {
                if( field.valueBase64!=null &&
                    field.valueBase64!="" &&
                    field.includeInSummary ) {

                        Image image = createImageWithKeyColor(field.valueBase64);

                        /*
                         * The magic below is to add a bottom line to
                         * an image.  We cannot just set border on the
                         * image, because the worder width will be
                         * scaled together with the image, and that is
                         * wrong.
                         *
                         * Also table2.setTotalWidth(150) does not
                         * work, is ignored, so we need to use
                         * table2.setWidthPercentage(50).
                         */
                        PdfPTable table2 = new PdfPTable(new float[]{1});
                        table2.setWidthPercentage(50);
                        table2.setHorizontalAlignment(Element.ALIGN_LEFT);

                        PdfPCell cell2 = new PdfPCell(image, true);
                        cell2.setBorder(Rectangle.BOTTOM);
                        /*
                         * For some reason bottom images in color
                         * space gray have calculated final dimensions
                         * in other way that rgb ones. This results in
                         * images partially obscuring bottom line in
                         * cell so it looks thinner than full line
                         * weight. Add artificial padding at the
                         * bottom so that ovelapping does not happen.
                         */
                        cell2.setPaddingBottom(1);
                        cell2.setBorderWidth(1f);
                        cell2.setBorderColor(lightTextColor);

                        table2.addCell(cell2);
                        cell.addElement(table2);
                }
            }
            String signedAtText = person.signedAtText;
            if(!signedAtText.equals("")) {
                para = createParagraph(signedAtText, 10, Font.ITALIC, lightTextColor);
                para.setLeading(0f, 1.2f);
                cell.addElement(para);
            }
            table.addCell(cell);
            cells++;
        }
        if( (cells&1)!=0 ) {
            PdfPCell cell = new PdfPCell();
            cell.setBorder(PdfPCell.NO_BORDER);
            table.addCell(cell);
        }

        document.add(table);
    }

    /*
     * Add a subtitle, care for its style.
     */
    public static void addSubtitle(Document document, String text)
        throws DocumentException, IOException
    {
        Paragraph para = createParagraph(text, 12, Font.NORMAL, darkTextColor);
        para.setFirstLineIndent(7f);
        para.setSpacingBefore(10);
        para.setSpacingAfter(10);
        document.add(para);
    }

    /*
     * Prepare seal pages that will be appended to a source PDF document.
     *
     * I did not find a way to do this without first serializing this
     * document.  Interestingly just appending pages in place did not
     * work with stamping.  itext seems limited here.
     */
    public static void prepareSealPages(final SealSpec spec, OutputStream os)
        throws IOException, DocumentException, Base64DecodeException
    {
        Document document = new Document();
        PdfWriter writer = PdfWriter.getInstance(document, os);
        PdfPageEvent pageEvent = new PdfPageEvent() {

			@Override
			public void onStartPage(PdfWriter arg0, Document arg1) {
				try {
			        PdfPTable table = new PdfPTable(1);
			        table.setWidthPercentage(100f);
			        PdfPCell cell = new PdfPCell();
			        cell.setBorder(PdfPCell.NO_BORDER);
			        cell.addElement(createParagraph(spec.staticTexts.verificationTitle, 21, Font.NORMAL, darkTextColor));
			        cell.addElement(createParagraph(spec.documentNumberText, 12, Font.NORMAL, lightTextColor));
			        cell.setPaddingBottom(12);
			        table.addCell(cell);
			        arg1.add(table);
				} catch (DocumentException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onSectionEnd(PdfWriter arg0, Document arg1, float arg2) {
			}

			@Override
			public void onSection(PdfWriter arg0, Document arg1, float arg2, int arg3,
					Paragraph arg4) {
			}

			@Override
			public void onParagraphEnd(PdfWriter arg0, Document arg1, float arg2) {
			}

			@Override
			public void onParagraph(PdfWriter arg0, Document arg1, float arg2) {
			}

			@Override
			public void onOpenDocument(PdfWriter arg0, Document arg1) {
			}

			@Override
			public void onGenericTag(PdfWriter arg0, Document arg1, Rectangle arg2,
					String arg3) {
			}

			@Override
			public void onEndPage(PdfWriter arg0, Document arg1) {
			}

			@Override
			public void onCloseDocument(PdfWriter arg0, Document arg1) {
			}

			@Override
			public void onChapterEnd(PdfWriter arg0, Document arg1, float arg2) {
			}

			@Override
			public void onChapter(PdfWriter arg0, Document arg1, float arg2,
					Paragraph arg3) {
			}
		};

		writer.setPageEvent(pageEvent);
        document.open();

        PdfPTableDrawFrameAroundTable drawFrame = new PdfPTableDrawFrameAroundTable();

        document.setMargins(document.leftMargin(),
                            document.rightMargin(),
                            document.topMargin(),
                            document.bottomMargin() + 130);

        writer.getPageDictEntries().put(scriveTag, scriveTagVerPage);

        Paragraph para = createParagraph("", 12, Font.NORMAL, lightTextColor);

        /*
         * Warning for future generations:
         *
         * itext will not show row of a table that is not full of
         * cells. You have to add one last empty cell to get it going.
         */

        /*
         * Document part
         */
        addSubtitle(document, spec.staticTexts.documentText);

        PdfPTable table = new PdfPTable(2);
        int cells;
        table.setWidthPercentage(100f);
        table.setWidths(new int[]{1, 1});

        cells = 0;
        for( FileDesc file : spec.filesList ) {
            PdfPCell cell;
            cell = new PdfPCell();
            cell.setBorderColor(frameColor);
            cell.setPadding(15);
            cell.setPaddingTop(5);
            cell.setPaddingBottom(12);
            cell.setBorderWidth(1f);

            para = createParagraph(file.title, 10, Font.BOLD, lightTextColor);
            para.setLeading(0f, 1.2f);
            cell.addElement(para);
            para = createParagraph(file.role, 10, Font.NORMAL, lightTextColor);
            para.setLeading(0f, 1.2f);
            cell.addElement(para);
            para = createParagraph(file.pagesText, 10, Font.NORMAL, lightTextColor);
            para.setLeading(0f, 1.2f);
            cell.addElement(para);

            if (file.attachedToSealedFileText != null) {
              para = createParagraph(file.attachedToSealedFileText, 10, Font.NORMAL, lightTextColor);
              para.setLeading(0f, 1.2f);
              cell.addElement(para);
            }

            para = createParagraph(file.attachedBy, 10, Font.ITALIC, lightTextColor);
            para.setLeading(0f, 1.2f);
            cell.addElement(para);
            table.addCell(cell);
            cells++;
        }
        if( (cells&1)!=0 ) {
            PdfPCell cell = new PdfPCell();
            cell.setBorder(PdfPCell.NO_BORDER);
            table.addCell(cell);
        }
        document.add(table);

        /*
         * Initiator part
         */
        if(spec.initiator != null) {
            addSubtitle(document, spec.staticTexts.initiatorText);
            addPersonsTable(Arrays.asList(spec.initiator), document, spec);
        }

        /*
         * Partners part
         */
        addSubtitle(document, spec.staticTexts.partnerText);
        addPersonsTable(spec.persons, document, spec);
        document.newPage();
        /*
         * History log part
         */
        addSubtitle(document, spec.staticTexts.eventsText);

        table = new PdfPTable(2);
        table.setWidthPercentage(100f);
        table.setWidths(new int[]{12, 20});

        table.setTableEvent(drawFrame);

        for( HistEntry entry: spec.history ) {

            PdfPCell cell;
            cell = new PdfPCell();
            cell.setBorder(0);
            cell.setPaddingLeft(15);

            para = createParagraph(entry.date, 10, Font.ITALIC, lightTextColor);
            para.setLeading(0f, 1.2f);
            cell.addElement(para);
            para = createParagraph(entry.address, 8, Font.ITALIC, lightTextColor);
            para.setLeading(0f, 1.2f);
            para.setSpacingAfter(7);
            cell.addElement(para);
            table.addCell(cell);

            cell = new PdfPCell();
            cell.setBorder(0);
            cell.setPaddingRight(15);
            para = createParagraph(entry.comment, 10, Font.ITALIC, lightTextColor);
            para.setLeading(0f, 1.2f);
            para.setSpacingAfter(7);
            cell.addElement(para);
            table.addCell(cell);
        }
        document.add(table);

        document.close();
    }

    public static void stampFooterOverSealPages(SealSpec spec, PdfReader reader, ByteArrayOutputStream sealPages)
        throws IOException, DocumentException {
        PdfStamper stamper = new PdfStamper(reader, sealPages);
        PdfReader sealMarker = getSealMarker();
        Rectangle pageFrame = new Rectangle(581.839f-567.36f, 14.37f, 581.839f, 813.12f + 14.37f);
        pageFrame.setBorderColor(frameColor);
        pageFrame.setBorderWidth(1);
        pageFrame.setBorder(15);


        Document document = new Document();
        document.open();

//        float printableWidth = 567f;
//        float printableMargin = 23f;
        Rectangle pageSize = document.getPageSize();

        Rectangle footerFrame = new Rectangle(document.leftMargin(), document.bottomMargin(),
                                              pageSize.getWidth() - document.rightMargin(), document.bottomMargin() + 80);
        footerFrame.setBorderColor(frameColor);
        footerFrame.setBorderWidth(1);
        footerFrame.setBorder(15);


        PdfImportedPage sealMarkerImported = stamper.getImportedPage(sealMarker, 1);

        Rectangle footerTextRect = new Rectangle(footerFrame.getLeft() + 15,
                                                 footerFrame.getBottom() + 7,
                                                 footerFrame.getRight() - sealMarkerImported.getWidth() - 5 - 15,
                                                 footerFrame.getTop() - 7);

        int pageCount = reader.getNumberOfPages();
        for( int i=1; i<=pageCount; i++ ) {
            PdfContentByte canvasUnder = stamper.getUnderContent(i);

            canvasUnder.rectangle(pageFrame);

            PdfContentByte canvasOver = stamper.getOverContent(i);

            canvasOver.rectangle(footerFrame);

            canvasOver.addTemplate(sealMarkerImported, footerFrame.getRight() - sealMarkerImported.getWidth() - 5,
                                   footerFrame.getBottom() +
                                   (footerFrame.getHeight() - sealMarkerImported.getHeight())/2);

            ColumnText columnText = new ColumnText(canvasOver);

            columnText.setSimpleColumn(footerTextRect);
            columnText.setLeading(0f, 1.2f);

            Paragraph para = createParagraph(spec.staticTexts.verificationFooter, 8, Font.NORMAL, lightTextColor);
            para.setLeading(0f, 1.2f);
            columnText.addElement(para);

            para = createParagraph(i + "/" + pageCount, 8, Font.NORMAL, lightTextColor);
            para.setLeading(3f, 1.2f);
            columnText.addElement(para);
            columnText.go();
        }
        stamper.close();
        reader.close();
    }

    public static void addFileAttachments(PdfReader reader, Iterable<FileAttachment> attachments, OutputStream os )
        throws IOException, DocumentException, Base64DecodeException
    {
        PdfStamper stamper = new PdfStamper(reader, os);

        for( FileAttachment attachment : attachments ) {
            stamper.addFileAttachment(attachment.name, attachment.content, null, attachment.name);
        }
        stamper.close();
        reader.close();
    }

    public static void appendSealAttachments(Iterable<SealAttachment> attachments, List<FileAttachment> fileAttachments )
        throws Base64DecodeException
    {
        for( SealAttachment attachment : attachments ) {
            byte data[] = Base64.decode(attachment.fileBase64Content);
            if( data==null ) {
                throw new Base64DecodeException();
            }
            fileAttachments.add(new FileAttachment(data, attachment.fileName));
        }
    }

    public void execute(InputStream pdf, OutputStream out)
        throws IOException, DocumentException, Base64DecodeException
    {
        if (pdf == null)
            pdf = new FileInputStream(spec.input);
        if (out == null)
            out = new FileOutputStream(spec.output);
       
        initializeBaseFonts(spec.fonts);
        if(!spec.preseal) {

            ByteArrayOutputStream sealPagesRaw = new ByteArrayOutputStream();
            ByteArrayOutputStream sealPages = new ByteArrayOutputStream();
            ByteArrayOutputStream sourceWithFields = new ByteArrayOutputStream();
            ByteArrayOutputStream concatenatedPdf = new ByteArrayOutputStream();

            ArrayList<PdfReader> pdfsToConcatenate = new ArrayList<PdfReader>();
            ArrayList<FileAttachment> fileAttachments = new ArrayList<FileAttachment>();

            for( int i=1; i<spec.filesList.size(); i++) {

                if( spec.filesList.get(i).input != null) {
                    // Attempt to inline file if image or PDF, otherwise make it a PDF file attachment.
                    // Adjust pagesText if image or if we need to make a PDF file attachment.
                    try {
                        PdfReader a;
                        try {
                            a = new PdfReader(pdfFromImageFile(spec.filesList.get(i).input).toByteArray());
                            spec.filesList.get(i).pagesText = spec.staticTexts.onePageText;

                        } catch (IOException e) {
                            a = new PdfReader(spec.filesList.get(i).input);
                        }
                        ByteArrayOutputStream a2 = addAttachmentFooter(spec, spec.filesList.get(i), a);
                        pdfsToConcatenate.add(new PdfReader(a2.toByteArray()));
                    } catch (IOException e) {
                        spec.filesList.get(i).pagesText = spec.staticTexts.hiddenAttachmentText;
                        fileAttachments.add(new FileAttachment(spec.filesList.get(i).input, new File(spec.filesList.get(i).input).getName()));
                    }
                }
            }

            prepareSealPages(spec, sealPagesRaw);

            stampFooterOverSealPages(spec, new PdfReader(sealPagesRaw.toByteArray()), sealPages);

            stampFieldsAndPaginationOverPdf(spec, TextDump.createFlattened(new PdfReader(pdf)), getAllFields(spec), getAllHighlightedImage(spec), sourceWithFields);


            pdfsToConcatenate.add(0,new PdfReader(sourceWithFields.toByteArray()));
            pdfsToConcatenate.add(1,new PdfReader(sealPages.toByteArray()));

            concatenatePdfsInto(pdfsToConcatenate, concatenatedPdf);

            appendSealAttachments(spec.attachments, fileAttachments);

            addFileAttachments(new PdfReader(concatenatedPdf.toByteArray()),
                               fileAttachments,
                               out);
        }
        else {
            stampFieldsAndPaginationOverPdf(spec, TextDump.createFlattened(new PdfReader(pdf)), getAllFields(spec), getAllHighlightedImage(spec), out);
        }
    }

    static BaseFont baseFonts[];

    static void initializeBaseFonts(ArrayList<String> fonts)
        throws DocumentException, IOException
    {
        if( baseFonts==null ) {
            if( fonts!=null ) {
                ArrayList<BaseFont> baseFonts1 = new ArrayList<BaseFont>();
                for( String fontPath : fonts ) {
                    BaseFont baseFont;
                    baseFont = BaseFont.createFont( fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    baseFont.setSubset(true);

                    baseFonts1.add(baseFont);
                }
                baseFonts = new BaseFont[baseFonts1.size()];
                baseFonts1.toArray(baseFonts);
            }
            else {
                // this is a backward compatibility fallback, should
                // be removed as soon as font list is propagated where
                // it should be

            	String [] res = new String[] {"assets/SourceSansPro-Light.ttf", "assets/NotoSans-Regular.ttf", "assets/NotoSansThai-Regular.ttf"};
                baseFonts = new BaseFont[res.length];
                for (int i = 0; i < res.length; ++i)
                	baseFonts[i] = BaseFont.createFont(Main.getResource(res[i]), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
        }
    }

    /*
     * Multiple returns in Java. Sorry about this.
     */
    static float fontBaseline;

    /*
     * For future generation messing with this code: proper iteration
     * over code-points in String is:
     *
     * String str = "....";
     * int offset = 0, strLen = str.length();
     * while (offset < strLen) {
     *   int curChar = str.codePointAt(offset);
     *   offset += Character.charCount(curChar);
     *   // do something with curChar
     * }
     *
     * iText currently represents chars as 16bit values so it cannot
     * use code points defined above that limit, but should it support
     * that we will be ready!
     */

    static Paragraph createParagraph(String text, float size, int style, BaseColor color)
        throws DocumentException, IOException
    {
        Paragraph para = new Paragraph();

        BaseFont baseFont = null;
        Chunk lastChunk = null;
        BaseFont lastBaseFont = null;

        for( int i=0; i<text.length(); i++ ) {
            char c = text.charAt(i);
            baseFont = null;
            for( BaseFont baseFont1: baseFonts ) {
                if( baseFont1.charExists(c)) {
                    baseFont = baseFont1;
                    break;
                }
            }
            if( i==0 && baseFont != null ) {
                /*
                 * Based on first character in the string we need to
                 * calculate font baseline.
                 */
                fontBaseline = baseFont.getFontDescriptor(BaseFont.ASCENT, size);
            }
            if( lastChunk!=null && (lastBaseFont==baseFont || baseFont==null) ) {
                lastChunk.append(String.valueOf(c));
            }
            else {
                if( lastChunk!=null ) {
                    para.add(lastChunk);
                }
                if( baseFont!=null ) {
                    Font font = new Font(baseFont, size, style, color);
                    lastBaseFont = baseFont;
                    lastChunk = new Chunk(String.valueOf(c),font);
                }
                else {
                    // we did not find a suitable font so we use the
                    // font of the character before it
                    lastChunk = new Chunk(String.valueOf(c));
                }
            }
        }
        if( lastChunk!=null ) {
            para.add(lastChunk);
        }

        return para;
    }

    static PdfReader sealMarkerCached;
    static PdfReader getSealMarker()
        throws DocumentException, IOException
    {
        if( sealMarkerCached==null ) {
            sealMarkerCached = new PdfReader(Main.getResource("assets/sealmarker.pdf"));
        }
        return sealMarkerCached;
    }


}
