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
import java.util.ArrayList;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import com.itextpdf.awt.geom.Rectangle2D;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;

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

public class ExtractTexts extends TextEngine {

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

        for (Map.Entry<Integer, ArrayList<ArrayList<CharPos>>> lines: text.getChars().entrySet())
            for (ArrayList<CharPos> line: lines.getValue()) {
                final boolean horizontal = line.get(0).isHorizontal(); // IMPORTANT: assume !horizontal => vertical, since only H/V text is allowed
                Rectangle2D b1 = line.get(0).getBounds();
                final float req = 0.2f * (float)b1.getHeight();
                if (horizontal) {
                    if ((b1.getMinY() + req > t) || (b1.getMaxY() - req < b))
                        continue;
                } else {
                    if ((b1.getMinX() + req > r) || (b1.getMaxX() - req < l))
                        continue;
                }
                String txt = "";
                for (CharPos cp: line) {
                    if (horizontal) {
                        if ((cp.getBounds().getMinX() + req > r) || (cp.getBounds().getMaxX() - req < l))
                            continue;
                    } else {
                        if ((cp.getBounds().getMinY() + req > t) || (cp.getBounds().getMaxY() - req < b))
                            continue;
                    }
                    txt += cp.c + " ";
                }
                if (!txt.isEmpty())
                    foundText.add(txt.substring(0,  txt.length() - 1));
            }
        return foundText;
    }
    
    ExtractTextSpec spec = null;
    
    @Override
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
    

    public static Rectangle getRectInRotatedCropBoxCoordinates(PageText.Rect crop, int rotate, ArrayList<Double> rect) {
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

    public void stampRects()
        throws IOException, DocumentException
    {
        stamper.setRotateContents(false);

        int i;
        for (i = 1; i <= text.info.numberOfPages; i++) {
            for(Rect rect : spec.rects) {
                if( rect.page==i ) {

                    PageText rl = text.text[rect.page-1];
                    Rectangle frame = getRectInRotatedCropBoxCoordinates(rl.pageSize, rl.pageRotation, rect.rect);
                    /*
                    Rectangle crop = text.text[i-1].pageSizeRotated;
                    double l = rect.rect.get(0)*crop.getWidth() + crop.getLeft();
                    double t = (1-rect.rect.get(1))*crop.getHeight() + crop.getBottom();
                    double r = rect.rect.get(2)*crop.getWidth() + crop.getLeft();
                    double b = (1-rect.rect.get(3))*crop.getHeight() + crop.getBottom();
                    */

                    PdfContentByte canvas = stamper.getOverContent(i);
                    frame.setBorderColor(colorFromArrayListFloat(rect.color));
                    frame.setBorderWidth(0.3f);
                    frame.setBorder(Rectangle.BOX);
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
                PageText.Rect media = text.text[0].pageSize;
                stamper.insertPage(i, new Rectangle(media.getLeft(), media.getBottom(), media.getLeft() + media.getWidth(), media.getBottom() + media.getHeight(), text.text[0].pageRotation ));

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

    @Override
    public YamlSpec getSpec() {
        return spec;
    }
    @Override
    public String getStampedOutput() {
        return spec.stampedOutput;
    }

    @Override
    public void execute(OutputStream out) throws IOException, DocumentException {


        spec.additionalInfo = text.info;
        final int npages = spec.numberOfPages = text.info.numberOfPages;

        /*
         * Some pages may have no rectangles to find text in. This
         * avoids parsing of pages that are not required.
         */

        for(Rect rect : spec.rects ) {

            if( rect.page>=1 && rect.page<=npages) {

                PageText rl = text.text[rect.page-1];
                Rectangle frame = getRectInRotatedCropBoxCoordinates(rl.pageSize, rl.pageRotation, rect.rect);
                double l = frame.getLeft();
                double t = frame.getTop();
                double r = frame.getRight();
                double b = frame.getBottom();

                rect.lines = new ArrayList<String>();
                for( String line : find(rl,l,b,r,t) ) {
                    line = line.replaceAll(PageText.WHITE_SPACE + "+"," ").trim();
                    if( !line.equals("")) {
                        rect.lines.add(line);
                    }
                }
            }
        }

        if( stamper != null ) {
            stampRects();
        }

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
