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

class Match
{
    public String text;
    public ArrayList<Integer> pages; // pages to search, 1-based
    public int index;                // which match is the looked for, 1-based

    // result output mode
    public Integer page;
    public ArrayList<Double> coords;

    /*
     * Note: iText as of version 5.4.5 does a very surprising job when
     * calculating font bounding box. Especially upper and lower
     * bounds seem to be usually too low.  Either they forgot to take
     * into account one of the font metrics or they miscalculate
     * something.  Anyway until this is fixed trying to rely on font
     * bounding box introduces compatibility nightmare as it will
     * force us to always rely on miscalculated bounding box.
     *
     * Therefore bounding box disabled until we find a customer for
     * this feature and are able to fix it.
     */
    /*
    public ArrayList<Double> bbox;
    */
};

class FindTextSpec
{
    public String input;
    public Boolean yamlOutput;
    public ArrayList<Match> matches;
    public String stampedOutput;


    /*
     * YAML is compatible with JSON (at least with the JSON we generate).
     *
     * It was the simplest method to read in JSON values.
     */
    public static Yaml getYaml() {

        Constructor constructor = new Constructor(FindTextSpec.class);
        constructor.setPropertyUtils(constructor.getPropertyUtils());
        constructor.getPropertyUtils().setSkipMissingProperties(true);
        /*
         * Java reflection is missing some crucial information about
         * elements of containers.  Add this information here.
         */
        TypeDescription extractTextDesc = new TypeDescription(FindTextSpec.class);
        extractTextDesc.putListPropertyType("matches", Match.class);
        //sealSpecDesc.putMapPropertyType("staticTexts", String.class, String.class);
        constructor.addTypeDescription(extractTextDesc);


        //TypeDescription matchDesc = new TypeDescription(Match.class);
        //matchDesc.putListPropertyType("pages", Integer.class);
        //constructor.addTypeDescription(matchDesc);

        Yaml yaml = new Yaml(constructor);
        return yaml;
    }

    public static FindTextSpec loadFromFile(String fileName) throws IOException {
        InputStream input = new FileInputStream(new File(fileName));
        Yaml yaml = getYaml();
        return (FindTextSpec)yaml.load(input);
    }
}

class MyRenderListener implements RenderListener
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
    public CharPos foundText;
    public int foundTextCount;

    private PdfStamper stamper;
    private int page;

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
         */
        double x, y;

         /*
          * Due to bugs in iText 5.4.5 this part isn't working
          * yet. The bx, by are coordinates of lower left corner. ex,
          * ey are coordinates of upper right corner. [bx by ex ey] is
          * the bounding box for this character.
          */
        /*
        double bx, by, ex, ey;
        */
        public String toString() {
            return c + "," + x + "," + y;
        }
    };

    public ArrayList<CharPos> allCharacters;

    MyRenderListener(PdfStamper stamper, int page)
    {
        this.stamper = stamper;
        this.page = page;
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
            /*
             * We deliberatelly ignore spaces as those are not
             * reliable in PDFs.
             */
            if( !text.equals(" ") && !text.equals("\t") &&
                !text.equals("\n")  && !text.equals("\r") &&
                !text.equals("\u00A0")) {
                CharPos cp = new CharPos();
                cp.c = text;
                LineSegment line = tri.getBaseline();
                Vector p = line.getStartPoint();
                cp.x = p.get(Vector.I1);
                cp.y = p.get(Vector.I2);

                line = tri.getDescentLine();
                p = line.getStartPoint();
                float bx = p.get(Vector.I1);
                float by = p.get(Vector.I2);

                line = tri.getAscentLine();
                p = line.getEndPoint();
                float ex = p.get(Vector.I1);
                float ey = p.get(Vector.I2);

                allCharacters.add(cp);

                if( stamper!=null ) {
                    PdfContentByte canvas = stamper.getOverContent(page);
                    Rectangle frame = new Rectangle((float)cp.x-1,
                                                    (float)cp.y-1,
                                                    (float)cp.x+1,
                                                    (float)cp.y+1);
                    frame.setBorderColor(new BaseColor(1f, 0f, 1f));
                    frame.setBorderWidth(1f);
                    frame.setBorder(15);
                    canvas.rectangle(frame);

                    frame = new Rectangle((float)bx,
                                          (float)by,
                                          (float)ex,
                                          (float)ey);
                    frame.setBorderColor(new BaseColor(1f, 0f, 1f));
                    frame.setBorderWidth(0.1f);
                    frame.setBorder(15);
                    canvas.rectangle(frame);
                }

                /*
                 * Y coordinate postfixup. It seems that all
                 * coordinates here are moved down by half of the
                 * difference between baseline and descent. Strange.
                 */
                /*
                double fixup = cp.y - cp.by;
                System.err.println("cp: " + cp);
                cp.y += fixup/2;
                cp.by += fixup/2;
                cp.ey += fixup/2;
                */
            }
        }
    }
    public void find(String needle, int index) {

        foundTextCount = 0;
        foundText = null;

        int i, k;
        for( i=0; i<allCharacters.size(); i++ ) {
            for( k=0; k<needle.length(); k++ ) {
                if( !allCharacters.get(i+k).c.equals(needle.substring(k,k+1))) {
                    break;
                }
            }
            if( k==needle.length()) {
                // found!
                foundTextCount = foundTextCount + 1;

                if( index == 1 ) {
                    CharPos cp = new CharPos();
                    cp.c = needle;
                    cp.x = allCharacters.get(i).x;
                    cp.y = allCharacters.get(i).y;
                    /*
                    cp.bx = allCharacters.get(i).bx;
                    cp.by = allCharacters.get(i).by;
                    cp.ex = allCharacters.get(i+k-1).ex;
                    cp.ey = allCharacters.get(i+k-1).ey;
                    */
                    foundText = cp;
                    return;
                }
                index = index - 1;
            }
        }
    }

    static double roundForCompare(double v) {
        // 8pt font as smallest font supported
        return Math.round(v/8.0) * 8.0;
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
                if( cp1.x < cp2.x )
                    return -1;
                else if( cp1.x > cp2.x )
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

public class FindTexts {

    public static void execute(String specFile, String inputOverride)
        throws IOException, DocumentException
    {
        FindTextSpec spec = FindTextSpec.loadFromFile(specFile);
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

    public static void execute(FindTextSpec spec)
        throws IOException, DocumentException
    {
        PdfReader reader = new PdfReader(spec.input);
        PdfReaderContentParser parser = new PdfReaderContentParser(reader);
        PdfStamper stamper = null;
        if( spec.stampedOutput!=null ) {
            stamper = new PdfStamper(reader, new FileOutputStream(spec.stampedOutput));
        }

        ArrayList<MyRenderListener> charsForPages =
            new ArrayList<MyRenderListener>();

        /*
         * Some pages may not require searching because for example
         * nobody requested any text positions from them. We could
         * skip those here.
         */

        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            MyRenderListener collectCharacters = new MyRenderListener(stamper, i);
            parser.processContent(i, collectCharacters);
            collectCharacters.finalizeSearch();
            charsForPages.add(collectCharacters);

        }

        for(Match match : spec.matches ) {
            int index = match.index;
            if( match.pages!= null ) {
                String textNoSpaces = match.text.replace(" ","").replace("\t","").replace("\n","").
                    replace("\r","").replace("\u00A0","");

                for (Integer i : match.pages) {
                    if( i>=1 && i<=charsForPages.size() && match.text!=null && !match.text.equals("")) {
                        MyRenderListener rl = charsForPages.get(i-1);
                        rl.find(textNoSpaces, index);
                        if( rl.foundText!=null ) {
                            match.page = i;
                            Rectangle crop = reader.getPageSizeWithRotation(i);
                            match.coords = new ArrayList<Double>();
                            match.coords.add(new Double((rl.foundText.x - crop.getLeft())/crop.getWidth()));
                            match.coords.add(new Double(1 - (rl.foundText.y - crop.getBottom())/crop.getHeight()));
                            /*
                              See note about bbox field in the struct Match.

                            match.bbox = new ArrayList<Double>();
                            match.bbox.add(new Double((rl.foundText.bx - crop.getLeft())/crop.getWidth()));
                            match.bbox.add(new Double(1 - (rl.foundText.by - crop.getBottom())/crop.getHeight()));
                            match.bbox.add(new Double((rl.foundText.ex - crop.getLeft())/crop.getWidth()));
                            match.bbox.add(new Double(1 - (rl.foundText.ey - crop.getBottom())/crop.getHeight()));
                            */

                            if( stamper!=null ) {
                                PdfContentByte canvas = stamper.getOverContent(i);
                                Rectangle frame = new Rectangle((float)rl.foundText.x-1,
                                                                (float)rl.foundText.y-1,
                                                                (float)rl.foundText.x+1,
                                                                (float)rl.foundText.y+1);
                                frame.setBorderColor(new BaseColor(0f, 1f, 0f));
                                frame.setBorderWidth(2f);
                                frame.setBorder(15);
                                canvas.rectangle(frame);
                            }
                            break;
                        }
                        else {
                            index = index - rl.foundTextCount;
                        }
                    }
                }
            }
        }

        if( stamper!=null ) {
            stamper.close();
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
        representer.addClassTag(FindTextSpec.class,Tag.MAP);

        Yaml yaml = new Yaml(representer, options);
        String json = yaml.dump(spec);

        // We need to force utf-8 encoding here.
        PrintStream out = new PrintStream(System.out, true, "utf-8");
        out.println(json);
    }
}
