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
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.parser.Vector;

/*
 * Class that directly serve deserialization of JSON data.
 */

class Rect
{
    public Integer page;
    public ArrayList<Double> rect;
    public ArrayList<Float> color;

    // result output mode
    public ArrayList<String> lines;

    public ArrayList<String> expected;
};

class MatchedTemplate
{
    public ArrayList<Float> color;
    public String title;
};

class ExtractTextSpec extends YamlSpec
{
    public Boolean yamlOutput;
    public ArrayList<Rect> rects;
    public String stampedOutput;
    public Integer numberOfPages;

    public ArrayList<MatchedTemplate> listOfTemplates;

    HashSet<Integer> getPages() {
        HashSet<Integer> pages = new HashSet<Integer>();
        for ( Rect r: rects )
            pages.add(r.page);
        return pages;
    }
    
    public PdfAdditionalInfo additionalInfo;

    static private ArrayList<TypeDescription> td = null;

    static public ArrayList<TypeDescription> getTypeDescriptors() {
        if (td == null) {
            td = new ArrayList<TypeDescription>();
            TypeDescription extractTextDesc = new TypeDescription(ExtractTextSpec.class);
            extractTextDesc.putListPropertyType("rects", Rect.class);
            extractTextDesc.putListPropertyType("listOfTemplates", MatchedTemplate.class);
            td.add(extractTextDesc);
        }
        return td;
    }
}

class VectorUtils {

//	public static Vector add(Vector v1, Vector v2) {
//		return new Vector(v1.get(Vector.I1) + v2.get(Vector.I1), v1.get(Vector.I2) + v2.get(Vector.I2), v1.get(Vector.I3) + v2.get(Vector.I3));
//	}

	public static float crossProduct(Vector v1, Vector v2) {
		return v1.get(Vector.I1) * v2.get(Vector.I2) - v1.get(Vector.I2) * v2.get(Vector.I1);
	}

}

public class ExtractTexts extends Engine {

    /*
     * Params l, b, r, t are in PDF points coordinates. Those take
     * into account that crop box does not have to begin in 0,0.
     *
     * Character is considered to be in a rect if point in the middle
     * of its baseline falls within rect (including border equality).
     */
    public ArrayList<String> find(PageText text, double l, double b, double r, double t) {
        
        double tmp = 0;
        if( l>r ) {
            tmp = l; l = r; r = tmp;
        }
        if( b>t ) {
            tmp = b; b = t; t = tmp;
        }
        ArrayList<String> foundText = new ArrayList<String>();

        CharPos last = null;
        for (CharPos cp: text.getChars()) {
            // check if the middle of baseline
            final float xm = cp.getX() + 0.5f * cp.getBaseX();
            final float ym = cp.getY() + 0.5f * cp.getBaseY();
            if((xm >= l) && (xm <= r) && (((ym >= b) && (ym <= t)) || ((ym >= t) && (ym <= b)))
                && cp.c.codePointAt(0)>=32 ) {

                String txt = cp.c;

                if ((last != null) && (last.c.equals(cp.c))) { // detect duplicated glyphs (poor man's bold)
                    if (0.5 < cp.covers(last))
                        continue;
                }
                if( last==null || (0 != last.cmpDir(cp)) || (0 != last.cmpLine(cp))) {
                    foundText.add("");
                }
                else {
                    final float d0 = 0.1f * (cp.getWidth() + last.getWidth());
                    final float dx = cp.getX() - last.getX2(), dy = cp.getY() - last.getY2(), dist2 = dx * dx + dy * dy;   
                    if (dist2 > d0 * d0) { // need to put a space in here
                        txt = " " + txt;
                    }
                }
                int idx = foundText.size()-1;
                foundText.set(idx, foundText.get(idx) + txt);
                last = cp;
            }
        }
        return foundText;
    }
    
    ExtractTextSpec spec = null;
    
    public void Init(InputStream specFile, String inputOverride, String outputOverride) throws IOException {
        // TODO: it would be nice if ExtractTextSpec added type descritpors itself, because we can forget to set them implicitly :-/   
        YamlSpec.setTypeDescriptors(ExtractTextSpec.class, ExtractTextSpec.getTypeDescriptors());
        spec = ExtractTextSpec.loadFromStream(specFile, ExtractTextSpec.class);
        if( inputOverride!=null ) {
            spec.input = inputOverride;
        }
        if( outputOverride!=null ) {
            spec.stampedOutput = outputOverride;
        }       
    }

    static BaseColor colorFromArrayListFloat(ArrayList<Float> color) {
        if( color!=null && color.size()==3) {
            return new BaseColor(color.get(0), color.get(1), color.get(2));
        }
        else {
            return new BaseColor(0f, 1f, 0f);
        }
    }
    

    public static Rectangle getRectInRotatedCropBoxCoordinates(Rectangle crop, int rotate, ArrayList<Double> rect) {
        /*
         * Here we fight in two coordinate spaces:
         * 1. Content coordinate space (the one when /Rotate is 0)
         * 2. Visible page coordinate space (differs when /Rotate is not 0)
         *
         * crop is in content coordinate space.
         * origrect is in visible page coordinate space but normalized to (0,0)-(1,1)
         *
         * What we need is to transform origrect BACK to unrotated
         * content coordinate space.  We probably could use some itext
         * mechanism for that but it proved to be inherently hard to
         * understand. So we do manual coordinate fiddling.
         */

        Rectangle origrect;
        origrect = new Rectangle((float)(double)rect.get(0), (float)(1-rect.get(1)), (float)(double)rect.get(2), (float)(1-rect.get(3)));

        double l = 0, t = 0, r = 0, b = 0;
        switch(rotate ) {
        default:
            l = origrect.getLeft()        * crop.getWidth()  + crop.getLeft();
            t = origrect.getBottom()      * crop.getHeight() + crop.getBottom();
            r = origrect.getRight()       * crop.getWidth()  + crop.getLeft();
            b = origrect.getTop()         * crop.getHeight() + crop.getBottom();
            break;
        case 90:
            l = (1-origrect.getTop())     * crop.getWidth()  + crop.getLeft();
            t = origrect.getLeft()        * crop.getHeight() + crop.getBottom();
            r = (1-origrect.getBottom())  * crop.getWidth()  + crop.getLeft();
            b = origrect.getRight()       * crop.getHeight() + crop.getBottom();
            break;
        case 180:
            l = (1-origrect.getRight())   * crop.getWidth()  + crop.getLeft();
            t = (1-origrect.getTop())     * crop.getHeight() + crop.getBottom();
            r = (1-origrect.getLeft())    * crop.getWidth()  + crop.getLeft();
            b = (1-origrect.getBottom())  * crop.getHeight() + crop.getBottom();
            break;
        case 270:
            l = origrect.getBottom()      * crop.getWidth()  + crop.getLeft();
            t = (1-origrect.getRight())   * crop.getHeight() + crop.getBottom();
            r = origrect.getTop()         * crop.getWidth()  + crop.getLeft();
            b = (1-origrect.getLeft())    * crop.getHeight() + crop.getBottom();
            break;
        }

        Rectangle rect2 = new Rectangle((float)l,(float)b,(float)r,(float)t);
        return rect2;
    }

    public static void stampRects(PdfReader reader, ExtractTextSpec spec)
        throws IOException, DocumentException
    {
        PdfStamper stamper = new PdfStamper(reader, new FileOutputStream(spec.stampedOutput));

        stamper.setRotateContents(false);

        int i;
        for (i = 1; i <= reader.getNumberOfPages(); i++) {
            for(Rect rect : spec.rects) {
                if( rect.page==i ) {

                    Rectangle frame = getRectInRotatedCropBoxCoordinates(reader.getPageSize(rect.page), reader.getPageRotation(rect.page), rect.rect);
                    /*
                    Rectangle crop = reader.getPageSizeWithRotation(rect.page);
                    double l = rect.rect.get(0)*crop.getWidth() + crop.getLeft();
                    double t = (1-rect.rect.get(1))*crop.getHeight() + crop.getBottom();
                    double r = rect.rect.get(2)*crop.getWidth() + crop.getLeft();
                    double b = (1-rect.rect.get(3))*crop.getHeight() + crop.getBottom();
                    */


                    PdfContentByte canvas = stamper.getOverContent(i);
                    frame.setBorderColor(colorFromArrayListFloat(rect.color));
                    frame.setBorderWidth(0.3f);
                    frame.setBorder(15);
                    canvas.rectangle(frame);
                }
            }
        }

        if( spec.listOfTemplates!=null ) {
            ColumnText ct = new ColumnText(null);
            for (MatchedTemplate mt : spec.listOfTemplates) {
                Paragraph p = new Paragraph(mt.title,
                                            FontFactory.getFont(FontFactory.HELVETICA, 12, Font.NORMAL,
                                                                colorFromArrayListFloat(mt.color)));
                ct.addElement(p);
            }

            while(true) {
                // Add a new page
                stamper.insertPage(i, reader.getPageSize(1));

                // Add as much content of the column as possible
                PdfContentByte canvas = stamper.getOverContent(i);
                ct.setCanvas(canvas);
                ct.setSimpleColumn(16, 16, 559, 770);
                if (!ColumnText.hasMoreText(ct.go()))
                    break;
                i++;
            }
        }

        stamper.close();
    }

    public void execute(InputStream pdf, OutputStream out)
        throws IOException, DocumentException
    {
        /* This is here to flatten forms so that texts in them can be read
         */
        if (pdf == null)
            pdf = new FileInputStream(spec.input);
        PdfReader reader = new PdfReader(pdf);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PdfStamper stamper = new PdfStamper(reader, os);

        spec.additionalInfo = new PdfAdditionalInfo();
        spec.additionalInfo.numberOfPages = reader.getNumberOfPages();
        Rectangle rx = reader.getPageSizeWithRotation(1);
        spec.additionalInfo.firstPageWidth = rx.getWidth();
        spec.additionalInfo.firstPageHeight = rx.getHeight();

        stamper.setFormFlattening(true);
        stamper.setFreeTextFlattening(true);

        stamper.close();
        reader.close();
        pdf.close();

        reader = new PdfReader(os.toByteArray());

        int npages = reader.getNumberOfPages();

        spec.numberOfPages = npages;

        PageText charsForPages[] = new PageText[npages];

        /*
         * Some pages may have no rectangles to find text in. This
         * avoids parsing of pages that are not required.
         */

        for(Rect rect : spec.rects ) {

            if( rect.page>=1 && rect.page<=npages) {

                PageText rl = charsForPages[rect.page-1];
                if( rl == null ) {
                    rl = new PageText(reader, rect.page, null);
                    charsForPages[rect.page-1] = rl;
                    spec.additionalInfo.containsControlCodes = spec.additionalInfo.containsControlCodes || rl.containsControlCodes();
                    spec.additionalInfo.containsGlyphs = spec.additionalInfo.containsGlyphs || rl.containsGlyphs();
                }

                Rectangle frame = getRectInRotatedCropBoxCoordinates(reader.getPageSize(rect.page), reader.getPageRotation(rect.page), rect.rect);
                double l = frame.getLeft();
                double t = frame.getTop();
                double r = frame.getRight();
                double b = frame.getBottom();

                rect.lines = new ArrayList<String>();
                for( String line : find(rl,l,b,r,t) ) {
                    line = line.replaceAll("[ \t\n\u000B\f\r\u00A0\uFEFF\u200B]+"," ");
                    if( !line.equals("")) {
                        int beginIndex = 0;
                        int endIndex = line.length();
                        char c = line.charAt(beginIndex);
                        if( " \t\n\u000B\f\r\u00A0\uFEFF\u200B".indexOf(c)>=0 ) {
                            beginIndex = 1;
                        }
                        c = line.charAt(endIndex-1);
                        if( " \t\n\u000B\f\r\u00A0\uFEFF\u200B".indexOf(c)>=0 ) {
                            endIndex = endIndex - 1;
                        }
                        if( beginIndex < endIndex ) {
                            line = line.substring(beginIndex,endIndex);
                        }
                        else {
                            line = "";
                        }
                    }
                    if( !line.equals("")) {
                        rect.lines.add(line);
                    }
                }
            }
        }


        if( spec.stampedOutput != null ) {
            stampRects(reader, spec);
        }

        reader.close();

        DumperOptions options = new DumperOptions();
        if( spec.yamlOutput!=null && spec.yamlOutput.equals(true)) {
            // output in yaml mode, useful for testing
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        }
        else {
            // output in json mode
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        }
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN );
        options.setPrettyFlow(false);
        options.setWidth(Integer.MAX_VALUE);
        Representer representer = new MyRepresenter();
        representer.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        representer.addClassTag(ExtractTextSpec.class,Tag.MAP);

        Yaml yaml = new Yaml(representer, options);
        String json = yaml.dump(spec);

        // We need to force utf-8 encoding here.
        PrintStream ps = new PrintStream((out == null) ? System.out : out, true, "utf-8");
        ps.println(json);
    }
}
