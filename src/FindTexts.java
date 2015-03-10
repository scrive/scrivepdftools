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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.parser.LineSegment;
import com.itextpdf.text.pdf.parser.Vector;

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
}

class FindTextSpec extends YamlSpec
{
    public Boolean yamlOutput;
    public ArrayList<Match> matches;
    public String stampedOutput;

    public PdfAdditionalInfo additionalInfo;

    static private ArrayList<TypeDescription> td = null;

    static public ArrayList<TypeDescription> getTypeDescriptors() {
        if (td == null) {
            td = new ArrayList<TypeDescription>();
            TypeDescription extractTextDesc = new TypeDescription(FindTextSpec.class);
            extractTextDesc.putListPropertyType("matches", Match.class);
            //sealSpecDesc.putMapPropertyType("staticTexts", String.class, String.class);
            td.add(extractTextDesc);

            //TypeDescription matchDesc = new TypeDescription(Match.class);
            //matchDesc.putListPropertyType("pages", Integer.class);
            //td.add(matchDesc);
            td.add(extractTextDesc);
        }
        return td;
    }
 }


public class FindTexts extends TextEngine
{
    private PdfContentByte canvas = null;
    
     public ArrayList<PageText.CharPos> find(PageText text, String needle, int index) {
         String cc = "";
         ArrayList<PageText.CharPos> foundText = new ArrayList<PageText.CharPos>();
         ArrayList<PageText.CharPos> all = text.getChars();
         for( PageText.CharPos c: all)
             cc += c.c;
         for (int i = cc.indexOf(needle, 0); i >= 0; i = cc.indexOf(needle,  i + 1)) {
             PageText.CharPos c0 = all.get(i);
             LineSegment line = new LineSegment(new Vector(c0.getX(), c0.getY(), 0.0f), new Vector(c0.getX2(), c0.getY2(), 0.0f));
             foundText.add(text.new CharPos(needle, line));
             if( index == 1 )
                 return foundText;
             else
                 --index;
         }
         // mark all glyphs
         if (stamper != null) {
             for( PageText.CharPos c: all) {
                 final float x = c.getX(), y = c.getY();
                 Rectangle frame = new Rectangle(x - 1, y - 1, x + 1, y + 1);
                 frame.setBorderColor(new BaseColor(1f, 0f, 1f));
                 frame.setBorderWidth(1f);
                 frame.setBorder(15);
                 canvas.rectangle(frame);
                 frame = new Rectangle(x, y, c.getX2(), c.getY2());
                 frame.setBorderColor(new BaseColor(1f, 0f, 1f));
                 frame.setBorderWidth(0.1f);
                 frame.setBorder(15);
                 canvas.rectangle(frame);
             }
         }
         return null;
     }

     FindTextSpec spec = null;
    
    public void Init(InputStream specFile, String inputOverride, String outputOverride) throws IOException {
        // TODO: it would be nice if FindTextSpec added type descritpors itself, because we can forget to set them implicitly :-/   
        YamlSpec.setTypeDescriptors(FindTextSpec.class, FindTextSpec.getTypeDescriptors());
        spec = FindTextSpec.loadFromStream(specFile, FindTextSpec.class);
        if( inputOverride!=null ) {
            spec.input = inputOverride;
        }
        if( outputOverride!=null ) {
            spec.stampedOutput = outputOverride;
        }       
    }

    @Override
    public YamlSpec getSpec() {
        return spec;
    }
    @Override
    public String getStampedOutput() {
        return spec.stampedOutput;
    }

    @Override
    public void execute(OutputStream out) {
        spec.additionalInfo = text.info;

        // Search for text
        for(Match match : spec.matches ) {
            int index = match.index;
            if( match.pages!= null ) {
                String textNoSpaces = match.text.replace(" ","").replace("\t","").replace("\n","").
                    replace("\r","").replace("\u00A0","");

                for (Integer ip : match.pages) {
                    int i = ip;
                    if( i<0 ) {
                        // -1 is last page, -2 is second to the last
                        i = text.info.numberOfPages + i + 1;
                    }
                    if( i>=1 && i<=text.info.numberOfPages && match.text!=null && !match.text.equals("")) {
                        if (stamper != null) {
                            canvas = stamper.getOverContent(i);
                        }
                        ArrayList<PageText.CharPos> found = find(text.text[i-1], textNoSpaces, index);
                        PageText.CharPos foundText = ((found == null) || found.isEmpty()) ? null : found.get(found.size() - 1);
                        if( foundText!=null ) {
                            match.page = i;
                            PageText.Rect crop = text.text[i-1].pageSizeRotated;
                            //Rectangle crop = bounds;
                            match.coords = new ArrayList<Double>();
                            match.coords.add(new Double((foundText.getX() - crop.getLeft())/crop.getWidth()));
                            match.coords.add(new Double(1 - (foundText.getY() - crop.getBottom())/crop.getHeight()));
                            /*
                              See note about bbox field in the struct Match.

                            match.bbox = new ArrayList<Double>();
                            match.bbox.add(new Double((rl.foundText.bx - crop.getLeft())/crop.getWidth()));
                            match.bbox.add(new Double(1 - (rl.foundText.by - crop.getBottom())/crop.getHeight()));
                            match.bbox.add(new Double((rl.foundText.ex - crop.getLeft())/crop.getWidth()));
                            match.bbox.add(new Double(1 - (rl.foundText.ey - crop.getBottom())/crop.getHeight()));
                            */

                            if( canvas!=null ) {
                                Rectangle frame = new Rectangle((float)foundText.getX()-1,
                                                                (float)foundText.getY()-1,
                                                                (float)foundText.getX()+1,
                                                                (float)foundText.getY()+1);
                                frame.setBorderColor(new BaseColor(0f, 1f, 0f));
                                frame.setBorderWidth(2f);
                                frame.setBorder(15);
                                canvas.rectangle(frame);
                            }
                            break;
                        }
                        else if (found != null) {
                            index = index - found.size();
                        }
                    }
                }
            }
        }

        DumperOptions options = new DumperOptions();
        if( spec.yamlOutput!=null && spec.yamlOutput.equals(true)) {
            // output in yaml mode, useful for testing
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        } else {
            // output in json mode
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        }
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN );
        options.setPrettyFlow(false);
        options.setWidth(Integer.MAX_VALUE);
        Representer representer = new MyRepresenter();
        representer.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        representer.addClassTag(FindTextSpec.class,Tag.MAP);
        Yaml yaml = new Yaml(representer, options);
        String json = yaml.dump(spec);

        // We need to force utf-8 encoding here.
        try {
            PrintStream ps = new PrintStream((out == null) ? System.out : out, true, "utf-8");
            ps.println(json);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace(System.err);
        }
    }
}
