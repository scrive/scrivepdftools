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
import java.io.PrintStream;
import java.util.*;
import java.net.URL;
import java.lang.Character.UnicodeBlock;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.nodes.*;
import org.yaml.snakeyaml.introspector.Property;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;
import com.itextpdf.text.pdf.parser.SimpleTextExtractionStrategy;
import com.itextpdf.text.pdf.parser.TextExtractionStrategy;
import com.itextpdf.text.pdf.parser.RenderListener;
import com.itextpdf.text.pdf.parser.ImageRenderInfo;
import com.itextpdf.text.pdf.parser.TextRenderInfo;
import com.itextpdf.text.pdf.parser.Vector;
import com.itextpdf.text.pdf.parser.LineSegment;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfSmartCopy;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.Font;
import com.itextpdf.text.Font.FontFamily;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfPTableEvent;

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

class ExtractTextSpec
{
    public String input;
    public Boolean yamlOutput;
    public ArrayList<Rect> rects;
    public String stampedOutput;
    public Integer numberOfPages;

    public ArrayList<MatchedTemplate> listOfTemplates;

    /*
     * YAML is compatible with JSON (at least with the JSON we generate).
     *
     * It was the simplest method to read in JSON values.
     */
    public static Yaml getYaml() {

        Constructor constructor = new Constructor(ExtractTextSpec.class);
        constructor.setPropertyUtils(constructor.getPropertyUtils());
        constructor.getPropertyUtils().setSkipMissingProperties(true);

        /*
         * Java reflection is missing some crucial information about
         * elements of containers.  Add this information here.
         */
        TypeDescription extractTextDesc = new TypeDescription(ExtractTextSpec.class);
        extractTextDesc.putListPropertyType("rects", Rect.class);
        extractTextDesc.putListPropertyType("listOfTemplates", MatchedTemplate.class);
        constructor.addTypeDescription(extractTextDesc);

        Yaml yaml = new Yaml(constructor);
        return yaml;
    }

    public static ExtractTextSpec loadFromFile(String fileName) throws IOException {
        InputStream input = new FileInputStream(new File(fileName));
        Yaml yaml = getYaml();
        return (ExtractTextSpec)yaml.load(input);
    }
}


class ExtractTextsRenderListener implements RenderListener
{
    /*
     * Java does not support multiple results from functions, so treat
     * these as results from 'find'.
     *
     * foundText is not null if text was found (taking into account
     * requested index). If this is null then foundTextCount should
     * say how many texts were found on the page. This should be less
     * than index. It should be substracted from index and used on the
     * next page as limit.
     */
    public ArrayList<String> foundText;

    public class CharPos {
        /*
         * This is Unicode code point as used by java.String, so it
         * may be a surrogate pair or some other crap. As soon as we
         * enter markets that need this we need to move to full
         * Unicode support. This requires involed changes everywhere
         * so beware to test this properly once we are there.
         */
        String c;
        /*
         * Coordinates as returned by iText. We know that y is font
         * baseline coordinate.
         *
         * bx, by are coordinates of lower left corner. ex, ey are
         * coordinates of upper right corner. [bx by ex ey] is the
         * bounding box for this character.
         */
        double x, y, bx, by, ex, ey;
        public String toString() {
            return c + "," + x + "," + y + "," + bx + "," + by + "," + ex + "," + ey;
        }
    };

    static double roundForCompare(double v) {
        // 8pt font as smallest font supported
        return Math.round(v/8.0) * 8.0;
    }

    public ArrayList<CharPos> allCharacters;

    ExtractTextsRenderListener()
    {
        allCharacters = new ArrayList<CharPos>();
    }
    public void beginTextBlock() {
    }
    public void endTextBlock() {
    }
    public void renderImage(ImageRenderInfo renderInfo) {
    }
    public void renderText(TextRenderInfo renderInfo) {

        List<TextRenderInfo> individualCharacters = renderInfo.getCharacterRenderInfos();

        for( TextRenderInfo tri: individualCharacters ) {
            String text = tri.getText();

            CharPos cp = new CharPos();
            cp.c = text;
            LineSegment line = tri.getBaseline();
            Vector p = line.getStartPoint();
            cp.x = p.get(Vector.I1);
            cp.y = p.get(Vector.I2);

            line = tri.getDescentLine();
            p = line.getStartPoint();
            cp.bx = p.get(Vector.I1);
            cp.by = p.get(Vector.I2);

            line = tri.getAscentLine();
            p = line.getEndPoint();
            cp.ex = p.get(Vector.I1);
            cp.ey = p.get(Vector.I2);

            allCharacters.add(cp);
        }
    }

    /*
     * Params l, b, r, t are in PDF points coordinates. Those take
     * into account that crop box does not have to begin in 0,0.
     *
     * Character is considered to be in a rect if point in the middle
     * of its baseline falls within rect (including border equality).
     */
    public void find(double l, double b, double r, double t) {

        foundText = new ArrayList<String>();

        int i;
        CharPos last = null;
        for( i=0; i<allCharacters.size(); i++ ) {
            CharPos cp = allCharacters.get(i);

            double x = (cp.bx + cp.ex)/2;
            if(    x>=l &&    x<=r &&
                   (cp.y>=b && cp.y<=t || cp.y>=t && cp.y<=b) &&
                cp.c.codePointAt(0)>=32 ) {

                String txt = cp.c;

                if( last==null || roundForCompare(last.y) != roundForCompare(cp.y) ) {
                    foundText.add("");
                }
                else if( cp.bx - last.ex > 8 ) {
                    // need to put a space in here
                    txt = " " + txt;
                }
                int idx = foundText.size()-1;
                foundText.set(idx, foundText.get(idx) + txt);
                last = cp;
            }
        }
    }
    public void finalizeSearch()
    {
        // we need to sort all characters top to bottom and left to right

        CharPosComparator comparator = new CharPosComparator();
        Collections.sort(allCharacters, comparator);
    }

    class CharPosComparator implements Comparator<CharPos> {

        @Override
        public int compare(CharPos cp1, CharPos cp2) {
            if (roundForCompare(cp1.y)==roundForCompare(cp2.y)) {
                if( cp1.bx < cp2.bx )
                    return -1;
                else if( cp1.bx > cp2.bx )
                    return 1;
                else return 0;

            } else if (cp1.y < cp2.y ) {
                return 1;
            }
            else {
                return -1;
            }
        }
    }
};

public class ExtractTexts {
    public static void execute(String specFile, String inputOverride)
        throws IOException, DocumentException
    {
        ExtractTextSpec spec = ExtractTextSpec.loadFromFile(specFile);
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


    static BaseColor colorFromArrayListFloat(ArrayList<Float> color) {
        if( color!=null && color.size()==3) {
            return new BaseColor(color.get(0), color.get(1), color.get(2));
        }
        else {
            return new BaseColor(0f, 1f, 0f);
        }
    }

    public static void stampRects(PdfReader reader, ExtractTextSpec spec)
        throws IOException, DocumentException
    {
        PdfStamper stamper = new PdfStamper(reader, new FileOutputStream(spec.stampedOutput));

        int i;
        for (i = 1; i <= reader.getNumberOfPages(); i++) {
            for(Rect rect : spec.rects) {
                if( rect.page==i ) {

                    Rectangle crop = reader.getPageSizeWithRotation(rect.page);
                    double l = rect.rect.get(0)*crop.getWidth() + crop.getLeft();
                    double t = (1-rect.rect.get(1))*crop.getHeight() + crop.getBottom();
                    double r = rect.rect.get(2)*crop.getWidth() + crop.getLeft();
                    double b = (1-rect.rect.get(3))*crop.getHeight() + crop.getBottom();

                    PdfContentByte canvas = stamper.getOverContent(i);
                    Rectangle frame = new Rectangle((float)l,(float)b,(float)r,(float)t);
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

    public static void execute(ExtractTextSpec spec)
        throws IOException, DocumentException
    {
        /* This is here to flatten forms so that texts in them can be read
         */
        PdfReader reader = new PdfReader(spec.input);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PdfStamper stamper = new PdfStamper(reader, os);

        stamper.setFormFlattening(true);
        stamper.setFreeTextFlattening(true);

        stamper.close();
        reader.close();

        reader = new PdfReader(os.toByteArray());
        PdfReaderContentParser parser = new PdfReaderContentParser(reader);

        int npages = reader.getNumberOfPages();

        spec.numberOfPages = npages;

        ExtractTextsRenderListener charsForPages[] = new ExtractTextsRenderListener[npages];

        /*
         * Some pages may have no rectangles to find text in. This
         * avoids parsing of pages that are not required.
         */

        for(Rect rect : spec.rects ) {

            if( rect.page>=1 && rect.page<=npages) {

                ExtractTextsRenderListener rl = charsForPages[rect.page-1];
                if( rl == null ) {
                    rl = new ExtractTextsRenderListener();
                    parser.processContent(rect.page, rl);
                    rl.finalizeSearch();
                    charsForPages[rect.page-1] = rl;
                }

                Rectangle crop = reader.getPageSizeWithRotation(rect.page);
                double l = rect.rect.get(0)*crop.getWidth() + crop.getLeft();
                double t = (1-rect.rect.get(1))*crop.getHeight() + crop.getBottom();
                double r = rect.rect.get(2)*crop.getWidth() + crop.getLeft();
                double b = (1-rect.rect.get(3))*crop.getHeight() + crop.getBottom();
                rl.find(l,b,r,t);
                rect.lines = new ArrayList<String>();
                for( String line : rl.foundText ) {
                    line = line.replaceAll("\\A[ \t\n\u000B\f\r\u00A0\uFEFF\u200B]+","");
                    line = line.replaceAll("[ \t\n\u000B\f\r\u00A0\uFEFF\u200B]\\z","");
                    line = line.replaceAll("[ \t\n\u000B\f\r\u00A0\uFEFF\u200B]+"," ");
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
        //Yaml yaml = new Yaml(options);
        Representer representer = new MyRepresenter();
        representer.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        representer.addClassTag(ExtractTextSpec.class,Tag.MAP);

        Yaml yaml = new Yaml(representer, options);
        String json = yaml.dump(spec);

        // We need to force utf-8 encoding here.
        PrintStream out = new PrintStream(System.out, true, "utf-8");
        out.println(json);
    }
}
