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
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.net.URL;
import java.net.MalformedURLException;
import java.lang.Character.UnicodeBlock;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.Constructor;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.BadElementException;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfSmartCopy;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.Font;
import com.itextpdf.text.Font.FontFamily;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.CMYKColor;
import com.itextpdf.text.pdf.PdfPTableEvent;

import org.bouncycastle.util.encoders.Base64;

/*
 * Class that directly serve deserialization of JSON data.
 */
class HistEntry
{
    public String date;
    public String comment;
    public String address;
}

class Person
{
    public String fullname;
    public String company;
    public String personalnumber;
    public String companynumber;
    public String email;
    public String phone;
    public Boolean fullnameverified;
    public Boolean companyverified;
    public Boolean numberverified;
    public Boolean emailverified;
    public Boolean phoneverified;
    public ArrayList<Field> fields;
    public String signtime;
}

class Field
{
    public String valueBase64;
    public String value;
    public float x, y;
    public int page;
    public float image_w;
    public float image_h;
    public Boolean includeInSummary;
    public Boolean onlyForSummary;
    public float fontSize;
    public Boolean greyed;
    public ArrayList<Integer> keyColor;
}

class SealingTexts
{
    public String verificationTitle;
    public String docPrefix;
    public String signedText;
    public String partnerText;
    public String initiatorText;
    public String documentText;
    public String orgNumberText;
    public String personalNumberText;
    public String eventsText;
    public String dateText;
    public String historyText;
    public String verificationFooter;
    public String hiddenAttachmentText;
    public String onePageText;
    public String signedAtText;
}

class SealAttachment
{
    public String fileName;
    public String mimeType;
    public String fileBase64Content;
}

class FileDesc
{
    public String title;
    public String role;
    public String pagesText;
    public String attachedBy;
    public String input;
}

class SealSpec
{
    public Boolean preseal;
    public String input;
    public String output;
    public String documentNumber;
    public ArrayList<Person> persons;
    public ArrayList<Person> secretaries;
    public Person initiator;
    public ArrayList<HistEntry> history;
    public String initials;
    public String hostpart;
    public SealingTexts staticTexts;
    public ArrayList<SealAttachment> attachments;
    public ArrayList<FileDesc> filesList;
    public ArrayList<Field> fields;

    /*
     * YAML is compatible with JSON (at least with the JSON we generate).
     *
     * It was the simplest method to read in JSON values.
     */
    public static Yaml getYaml() {
        Constructor constructor = new Constructor(SealSpec.class);
        constructor.getPropertyUtils().setSkipMissingProperties(true);

        /*
         * Java reflection is missing some crucial information about
         * elements of containers.  Add this information here.
         */
        TypeDescription sealSpecDesc = new TypeDescription(SealSpec.class);
        sealSpecDesc.putListPropertyType("persons", Person.class);
        sealSpecDesc.putListPropertyType("secretaries", Person.class);
        sealSpecDesc.putListPropertyType("history", HistEntry.class);
        sealSpecDesc.putMapPropertyType("staticTexts", String.class, String.class);
        sealSpecDesc.putListPropertyType("attachments", SealAttachment.class);
        sealSpecDesc.putListPropertyType("filesList", FileDesc.class);
        constructor.addTypeDescription(sealSpecDesc);


        TypeDescription personDesc = new TypeDescription(Person.class);
        personDesc.putListPropertyType("fields", Field.class);
        constructor.addTypeDescription(personDesc);

        Yaml yaml = new Yaml(constructor);
        return yaml;
    }

    public static SealSpec loadFromFile(String fileName) throws IOException {
        InputStream input = new FileInputStream(new File(fileName));
        Yaml yaml = getYaml();
        return (SealSpec)yaml.load(input);
    }
}

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


public class AddVerificationPages {


    /*
     * Define some colors.
     */

    static CMYKColor darkTextColor = new CMYKColor(0.806f, 0.719f, 0.51f, 0.504f);
    static CMYKColor lightTextColor = new CMYKColor(0.597f, 0.512f, 0.508f, 0.201f);
    static CMYKColor frameColor = new CMYKColor(0f, 0f, 0f, 0.333f);

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

        for(PdfReader reader: sources) {
            int count = reader.getNumberOfPages();
            for( int i=1; i<=count; i++ ) {
                PdfImportedPage page = writer.getImportedPage(reader, i);
                writer.addPage(page);
            }
            writer.freeReader(reader);
            reader.close();
        }

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
            if(spec.preseal == null || !spec.preseal) {
                addPaginationFooter(spec, stamper, canvas, cropBox,
                        spec.staticTexts.docPrefix + " " + spec.documentNumber,
                        file.role);
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
    public static void stampFieldsAndPaginationOverPdf(SealSpec spec, PdfReader reader, ArrayList<Field> fields, OutputStream os)
        throws DocumentException, IOException, Base64DecodeException
    {
        PdfStamper stamper = new PdfStamper(reader, os);

        stamper.setFormFlattening(true);
        stamper.setFreeTextFlattening(true);

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

            PdfContentByte canvas = stamper.getOverContent(i);
            for( Field field : fields ) {
                if( field.page==i &&
                    (field.onlyForSummary == null || !field.onlyForSummary)) {

                    if( field.value != null ) {
                        /*
                         * This font characteristics were taken from
                         * Helvetica.afm that is one of standard Adobe
                         * PDF 14 fonts.
                         */
                        float fontBaseline = 931f/(931f+225f);
                        float fontOffset   = 166f/(931f+225f);
                        float fs = field.fontSize * cropBox.getWidth();

                        if( fs<=0 )
                            fs = 10f;

                        float realx = field.x * cropBox.getWidth() + cropBox.getLeft() - fontOffset * fs;
                        float realy = (1 - field.y) * cropBox.getHeight() + cropBox.getBottom() - fontBaseline * fs;

                        BaseColor color;
                        if( !field.greyed ) {
                            color = new CMYKColor(0,0,0,1f);
                        }
                        else {
                            color = new CMYKColor(0,0,0,127);
                        }

                        Paragraph para = createParagraph(field.value, fs, Font.NORMAL, color);

                        ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                                                   para,
                                                   realx, realy,
                                                   0);
                    }
                    else if( field.valueBase64 !=null ) {

                        float realx = field.x * cropBox.getWidth() + cropBox.getLeft();
                        float realy = (1 - field.y - field.image_h) * cropBox.getHeight() + cropBox.getBottom();

                        Image image = createImageWithKeyColor(field);

                        image.setAbsolutePosition(realx, realy);
                        image.scaleAbsoluteWidth(field.image_w * cropBox.getWidth());
                        image.scaleAbsoluteHeight(field.image_h * cropBox.getHeight());

                        canvas.addImage(image);
                    }
                }
            }
            if(spec.preseal == null || !spec.preseal) {
                addPaginationFooter(spec, stamper, canvas, cropBox,
                        spec.staticTexts.docPrefix + " " + spec.documentNumber,
                        spec.staticTexts.signedText + ": " + spec.initials);
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
    }


    /*
     * Gather all fields from all places they could be in SealSpec.
     */
    public static ArrayList<Field> getAllFields(SealSpec spec)
    {
        ArrayList<Field> result = new ArrayList<Field>();
        if( spec.persons!=null ) {
            for( Person person : spec.persons ) {
                if( person.fields!=null ) {
                    result.addAll(person.fields);
                }
            }
        }
        if( spec.secretaries!=null ) {
            for( Person person : spec.secretaries ) {
                if( person.fields!=null ) {
                    result.addAll(person.fields);
                }
            }
        }
        if( spec.fields!=null ) {
            result.addAll(spec.fields);
        }
        return result;
    }

    static Image createImageWithKeyColor(Field field) throws Base64DecodeException, BadElementException, MalformedURLException, IOException
    {
        byte rawdata[] = Base64.decode(field.valueBase64);
        if( rawdata==null ) {
            throw new Base64DecodeException();
        }

        Image image = Image.getInstance(rawdata);

        if( field.keyColor!=null ) {
            /*
             * Color space is 3 for RGB, 1 for
             * Gray. No idea what to do if this
             * returns something else.
             */
            int colorSpace = image.getColorspace();

            int r = field.keyColor.get(0);
            int g = field.keyColor.get(1);
            int b = field.keyColor.get(2);
            int ave = (r + g + b) / 3;

            switch(colorSpace) {
            case 3: // RGB
                image.setTransparency(new int[] { r, r, g, g, b, b } );
                break;
            case 1: // Gray, this is negative color space
                image.setTransparency(new int[] { 255 - ave, 255 - ave } );
                break;
            }
        }
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

            if( person.fullname!=null && !person.fullname.equals("") ) {
                para = createParagraph(person.fullname, 10,
                                       person.fullnameverified ? Font.BOLDITALIC : Font.BOLD,
                                       lightTextColor );
                para.setLeading(0f, 1.2f);
                cell.addElement(para);
            }
            if( person.company!=null && !person.company.equals("") ) {
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
            if( person.personalnumber!=null && !person.personalnumber.equals("") ) {
                para = createParagraph(spec.staticTexts.personalNumberText + " " + person.personalnumber, 10,
                                       person.numberverified ? Font.ITALIC : Font.NORMAL,
                                       lightTextColor );
                para.setLeading(0f, 1.2f);
                cell.addElement(para);
            }
            if( person.companynumber!=null && !person.companynumber.equals("") ) {
                para = createParagraph(spec.staticTexts.orgNumberText + " " + person.companynumber, 10,
                                       person.companyverified ? Font.ITALIC : Font.NORMAL,
                                       lightTextColor);
                para.setLeading(0f, 1.2f);
                cell.addElement(para);
            }
            if( person.email!=null && !person.email.equals("") ) {
                para = createParagraph(person.email, 10,
                                       person.emailverified ? Font.ITALIC : Font.NORMAL,
                                       lightTextColor);
                para.setLeading(0f, 1.2f);
                cell.addElement(para);
            }
            if( person.phone!=null && !person.phone.equals("") ) {
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

                        Image image = createImageWithKeyColor(field);

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
            if( person.signtime!=null && !person.signtime.equals("") ) {
                para = createParagraph(spec.staticTexts.signedAtText + " " + person.signtime, 10, Font.NORMAL, lightTextColor);
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
    public static void prepareSealPages(SealSpec spec, OutputStream os)
        throws IOException, DocumentException, Base64DecodeException
    {
        Document document = new Document();
        PdfWriter writer = PdfWriter.getInstance(document, os);

        document.open();

        PdfPTableDrawFrameAroundTable drawFrame = new PdfPTableDrawFrameAroundTable();

        Font font;

        document.setMargins(document.leftMargin(),
                            document.rightMargin(),
                            document.topMargin(),
                            document.bottomMargin() + 130);

        document.newPage();

        document.add(createParagraph(spec.staticTexts.verificationTitle, 21, Font.NORMAL, darkTextColor));

        Paragraph para = createParagraph(spec.staticTexts.docPrefix + " " + spec.documentNumber, 12, Font.NORMAL, lightTextColor);
        para.setSpacingAfter(50);
        document.add(para);

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
        if( spec.initiator!=null) {
            addSubtitle(document, spec.staticTexts.initiatorText);
            addPersonsTable(Arrays.asList(spec.initiator), document, spec);
        }

        /*
         * Partners part
         */
        addSubtitle(document, spec.staticTexts.partnerText);
        addPersonsTable(spec.persons, document, spec);

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

        float printableWidth = 567f;
        float printableMargin = 23f;
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

    public static void execute(String specFile, String inputOverride)
        throws IOException, DocumentException, Base64DecodeException
    {
        SealSpec spec = SealSpec.loadFromFile(specFile);
        if( inputOverride!=null ) {
            spec.input = inputOverride;
        }

        /*
          DumperOptions options = new DumperOptions();
          Yaml yaml = new Yaml(options);
          options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
          System.out.println(yaml.dump(spec));
        */
        execute(spec);

    }

    public static void execute(SealSpec spec)
        throws IOException, DocumentException, Base64DecodeException
    {
        if( spec.preseal==null || !spec.preseal ) {

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

            stampFieldsAndPaginationOverPdf(spec, new PdfReader(spec.input), getAllFields(spec), sourceWithFields);


            pdfsToConcatenate.add(0,new PdfReader(sourceWithFields.toByteArray()));
            pdfsToConcatenate.add(1,new PdfReader(sealPages.toByteArray()));

            concatenatePdfsInto(pdfsToConcatenate, concatenatedPdf);

            appendSealAttachments(spec.attachments, fileAttachments);

            addFileAttachments(new PdfReader(concatenatedPdf.toByteArray()),
                               fileAttachments,
                               new FileOutputStream(spec.output));
        }
        else {
            stampFieldsAndPaginationOverPdf(spec, new PdfReader(spec.input), getAllFields(spec), new FileOutputStream(spec.output));
        }
    }

    static Boolean hasCJK(String str) {
        /*
         * Warning: Java represents 16bit values as 'char'. This is
         * not a Unicode code point!  Basically surrogate pairs are
         * represented as two separate chars. This means the following
         * needs to be adjusted to cover chars if the range
         * 0x10000-0x1FFFF.
         *
         * For now I have no idea how to do this correctly.
         */
        for( int i=0; i<str.length(); i++ ) {
            char c = str.charAt(i);
            UnicodeBlock cblock = UnicodeBlock.of(c);
            if( cblock==UnicodeBlock.CJK_COMPATIBILITY
                || cblock==UnicodeBlock.CJK_COMPATIBILITY_FORMS
                || cblock==UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || cblock==UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT
                || cblock==UnicodeBlock.CJK_RADICALS_SUPPLEMENT
                || cblock==UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || cblock==UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || cblock==UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || cblock==UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                /* These are present in newer Java:
                || cblock==UnicodeBlock.CJK_STROKES
                || cblock==UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
                || cblock==UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D */
                )
                return true;
        }
        return false;
    }

    static BaseFont baseFontHelvetica;
    static BaseFont baseFontKochiMincho;

    /*
     * Fontology in PDF seems to be the most annoying thing in the world.
     *
     * For all reasonable alphabets we use Helvetica.ttf that we embed
     * inside jar file.
     *
     * CJK case needs a bit more research. As it seems proper font
     * needs to be chosen for each of the scripts. Font needs to be
     * embedded. As example how to create a Chineese font:
     *
     * BaseFont bf = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.EMBEDDED);
     * Font font = new Font(bf, 12)
     *
     */

    static Font getFontForString(String text, float size, int style, BaseColor color)
        throws DocumentException, IOException
    {
        Font font = null;
        /*
         * At this point we do not support Chinese, but should it be
         * needed it can be added as in the below code. Just get a
         * proper font and use it for chinese like strings.
        */
        /*
        if( font==null && hasCJK(text)) {
            if(baseFontKochiMincho==null ) {
                baseFontKochiMincho = BaseFont.createFont("public/fonts/kochi-mincho-subst.ttf",  BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                baseFontKochiMincho.setSubset(true);
            }
            font = new Font(baseFontKochiMincho, size, style, color);
        }
        */
        if( font==null ) {
            if(baseFontHelvetica==null ) {

                /* Investigate using FontFactory */
                baseFontHelvetica = BaseFont.createFont( AddVerificationPages.class.getResource("assets/SourceSansPro-Light.ttf").toString(),
                                                         BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                baseFontHelvetica.setSubset(true);
            }
            font = new Font(baseFontHelvetica, size, style, color);
        }
        return font;
    }

    static Paragraph createParagraph(String text, float size, int style, BaseColor color)
        throws DocumentException, IOException
    {
        Font font = getFontForString(text, size, style, color);
        return new Paragraph(text, font);
    }

    static PdfReader sealMarkerCached;
    static PdfReader getSealMarker()
        throws DocumentException, IOException
    {
        if( sealMarkerCached==null ) {
            sealMarkerCached = new PdfReader(AddVerificationPages.class.getResource("assets/sealmarker.pdf"));
        }
        return sealMarkerCached;
    }


}
