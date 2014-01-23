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
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.net.URL;
import java.lang.Character.UnicodeBlock;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.nodes.*;

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

/*
 * Class that directly serve deserialization of JSON data.
 */

class Rect
{
    public Integer page;
    public ArrayList<Double> rect;

    // result output mode
    public ArrayList<String> lines;
};

class ExtractTextSpec
{
    public String input;
    public ArrayList<Rect> rects;


    /*
     * YAML is compatible with JSON (at least with the JSON we generate).
     *
     * It was the simplest method to read in JSON values.
     */
    public static Yaml getYaml() {

        Constructor constructor = new Constructor(ExtractTextSpec.class);
        /*
         * Java reflection is missing some crucial information about
         * elements of containers.  Add this information here.
         */
        TypeDescription extractTextDesc = new TypeDescription(ExtractTextSpec.class);
        extractTextDesc.putListPropertyType("rects", Rect.class);
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
    public String foundText;

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

    public void find(double l, double b, double r, double t) {

        foundText = "";

        int i;
        for( i=0; i<allCharacters.size(); i++ ) {
            CharPos cp = allCharacters.get(i);
            if( cp.x>=l && cp.x<=r &&
                cp.y>=b && cp.y>=t ) {
                foundText = foundText + cp.c;
            }
        }
    }
};

public class ExtractTexts {
    /*
     * MyRepresenter exists for the sole purpose of putting strings in
     * double quotes.
     *
     * Using DumperOptions.ScalarStyle.DOUBLE_QUOTED had this additional
     * feature of putting also numbers (ints and floats) in quotes and
     * tagging them with tags. To get around this I have to write this
     * function.
     *
     * This has also the nice property of putting key names in double
     * quotes producing well formed json.
     */
    private static class MyRepresenter extends Representer
    {
        protected Node representScalar(Tag tag,
                                   String value,
                                       Character c) {
            if( tag==Tag.STR ) {
                return super.representScalar(tag,value,DumperOptions.ScalarStyle.DOUBLE_QUOTED.getChar());
            }
            else {
                return super.representScalar(tag,value,c);
            }
        }
    };


    public static void execute(String specFile)
        throws IOException, DocumentException
    {
        ExtractTextSpec spec = ExtractTextSpec.loadFromFile(specFile);

        /*
          DumperOptions options = new DumperOptions();
          Yaml yaml = new Yaml(options);
          options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
          System.out.println(yaml.dump(spec));
        */
        execute(spec);

    }

    public static void execute(ExtractTextSpec spec)
        throws IOException, DocumentException
    {
        PdfReader reader = new PdfReader(spec.input);
        PdfReaderContentParser parser = new PdfReaderContentParser(reader);

        ArrayList<ExtractTextsRenderListener> charsForPages =
            new ArrayList<ExtractTextsRenderListener>();

        /*
         * Some pages may not require searching because for example
         * nobody requested any text positions from them. We could
         * skip those here.
         */

        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            ExtractTextsRenderListener collectCharacters = new ExtractTextsRenderListener();
            parser.processContent(i, collectCharacters);
            charsForPages.add(collectCharacters);

        }

        for(Rect rect : spec.rects ) {

            if( rect.page>=1 && rect.page<=charsForPages.size()) {
                ExtractTextsRenderListener rl = charsForPages.get(rect.page-1);
                Rectangle crop = reader.getPageSizeWithRotation(rect.page);
                double l = rect.rect.get(0)*crop.getWidth();
                double b = rect.rect.get(1)*crop.getHeight();
                double r = rect.rect.get(2)*crop.getWidth();
                double t = rect.rect.get(3)*crop.getHeight();
                rl.find(l,b,t,r);
                rect.lines = new ArrayList<String>();
                rect.lines.add(rl.foundText);
            }
        }

        reader.close();

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN );
        options.setPrettyFlow(false);
        //Yaml yaml = new Yaml(options);
        Representer representer = new MyRepresenter();
        representer.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);

        Yaml yaml = new Yaml(representer, options);
        String json = yaml.dump(spec);

        // Yaml generates a type marker !!FindTextSpec in output
        // that I have no idea how to suppress.  I'm going to remove
        // it now.

        json = json.substring(json.indexOf("{"));

        // We need to force utf-8 encoding here.
        PrintStream out = new PrintStream(System.out, true, "utf-8");
        out.println(json);
    }
}
