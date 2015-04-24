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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    public ArrayList<CharPos> find(PageText text, String needle, int index) {
        if (needle.isEmpty())
            return null;
        ArrayList<CharPos> foundText = new ArrayList<CharPos>();
        for (Map.Entry<Integer, ArrayList<ArrayList<CharPos>>> lines: text.getChars().entrySet())
            for (ArrayList<CharPos> line: lines.getValue()) {
                String txt = "";
                for (CharPos cp: line)
                    txt += cp.c;
                for (int i = txt.indexOf(needle, 0); i >= 0; i = txt.indexOf(needle,  i + 1)) {
                    CharPos c0 = line.get(i);
                    LineSegment base = new LineSegment(new Vector(c0.getX(), c0.getY(), 0.0f), new Vector(c0.getX2(), c0.getY2(), 0.0f));
                    foundText.add(new CharPos(needle, base));
                    if( index == 1 )
                        return foundText;
                    else
                        --index;
                }
            }
         return foundText;
     }

    // mark all glyphs
    public void stampText(Set<Integer> stamped, int iPage) {
        if ((stamper == null) || (text.text.length < iPage) || stamped.contains(iPage))
            return;
        stamped.add(iPage);
        PdfContentByte canvas = stamper.getOverContent(iPage);
        for (Map.Entry<Integer, ArrayList<ArrayList<CharPos>>> lines: text.text[iPage - 1].getChars().entrySet()) {
            for (ArrayList<CharPos> line: lines.getValue()) {
                for (CharPos c: line) {
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
        }
    }

    FindTextSpec spec = null;

    @Override
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

    private void onTextFound(Match match, CharPos foundText)
    {
        PageText.Rect crop = text.text[match.page - 1].pageSizeRotated;
        match.coords = new ArrayList<Double>();
        match.coords.add(new Double((foundText.getX() - crop.getLeft()) / crop.getWidth()));
        match.coords.add(new Double(1 - (foundText.getY() - crop.getBottom()) / crop.getHeight()));

        /* See note about bbox field in the struct Match.
        match.bbox = new ArrayList<Double>();
        match.bbox.add(new Double((rl.foundText.bx - crop.getLeft())/crop.getWidth()));
        match.bbox.add(new Double(1 - (rl.foundText.by - crop.getBottom())/crop.getHeight()));
        match.bbox.add(new Double((rl.foundText.ex - crop.getLeft())/crop.getWidth()));
        match.bbox.add(new Double(1 - (rl.foundText.ey - crop.getBottom())/crop.getHeight()));
        */
        if( stamper!=null ) {
            Rectangle frame = new Rectangle((float)foundText.getX()-1,
                                            (float)foundText.getY()-1,
                                            (float)foundText.getX()+1,
                                            (float)foundText.getY()+1);
            frame.setBorderColor(new BaseColor(0f, 1f, 0f));
            frame.setBorderWidth(2f);
            frame.setBorder(15);
            stamper.getOverContent(match.page).rectangle(frame);
        }
    }

    @Override
    public void execute(OutputStream out) {
        spec.additionalInfo = text.info;
        final int pageCount = text.info.numberOfPages;
        ArrayList<Integer> pages0 = new ArrayList<Integer>(pageCount);
        for ( int i = 0; i < pageCount; ++i )
            pages0.add(i + 1);

        // mark all glyphs in all searched pages
        if (stamper != null) {
            Set<Integer> stamped = new HashSet<Integer>();
            for(Match match : spec.matches ) {
                ArrayList<Integer> pages = (match.pages != null) ? match.pages : pages0;
                for (Integer ip : pages)
                    stampText(stamped, (ip < 0) ? pageCount + ip + 1 : ip); // -1 is last page, -2 is second to the last
            }
        }

        // Search for text
        for(Match match : spec.matches ) {
            int index = match.index;
            String textNoSpaces = match.text.replace(" ","").replace("\t","").replace("\n","").
                replace("\r","").replace("\u00A0","");

            ArrayList<Integer> pages = (match.pages != null) ? match.pages : pages0;
            for (Integer ip : pages) {
                final int i = (ip < 0) ? pageCount + ip + 1 : ip; // -1 is last page, -2 is second to the last
                if( i>=1 && i<=pageCount && match.text!=null && !textNoSpaces.isEmpty() && (index>0)) {
                    ArrayList<CharPos> found = find(text.text[i-1], textNoSpaces, index);
                    if (index <= found.size()) {
                        match.page = i;
                        onTextFound(match, found.get(index - 1));
                        break;
                    } else if (found != null)
                        index = index - found.size();
                }
            }
        }
        printYaml(out);
    }

    private void printYaml(OutputStream out)
    {
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
