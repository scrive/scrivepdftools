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

    public PdfAdditionalInfo additionalInfo;

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

class VectorUtils {

	public static Vector add(Vector v1, Vector v2) {
		return new Vector(v1.get(Vector.I1) + v2.get(Vector.I1), v1.get(Vector.I2) + v2.get(Vector.I2), v1.get(Vector.I3) + v2.get(Vector.I3));
	}

	public static double crossProduct(Vector v1, Vector v2) {
		return v1.get(Vector.I1) * v2.get(Vector.I2) - v1.get(Vector.I2) * v2.get(Vector.I1);
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
     * than index. It should be subtracted from index and used on the
     * next page as limit.
     */
    public ArrayList<String> foundText;

    public boolean containsGlyphs;
    public boolean containsControlCodes;

    public class CharPos {
    	/*
         * This is Unicode code point as used by java.String, so it
         * may be a surrogate pair or some other crap. As soon as we
         * enter markets that need this we need to move to full
         * Unicode support. This requires involved changes everywhere
         * so beware to test this properly once we are there.
         */
        String c;
        /*
         * Coordinates as returned by iText. We know that y is font
         * baseline coordinate.
         */
        private Vector origin;
        private Vector base; // baseline = glyph direction + length

        /*
         *  Maximum vertical distance between two glyphs that can be assigned to the same line
         */
    	public static final double LINE_TOL = 4.0;

        public CharPos(String text, LineSegment base)
        {
        	c = text;
        	// save base line
        	origin = base.getStartPoint();
        	this.base = base.getEndPoint().subtract(origin);
        }

        public Vector getOrigin() {
        	return origin;
        }
        public Vector getBase()   {
        	return base;
        }

        /*
         * Returns the second point of a glyph's baseline
         */
        public Vector getEndPoint() {
        	return VectorUtils.add(origin, base);
        }

        /*
         * Returns glyph's width
         */
        public double getWidth() {
        	return base.length();
        }

        /*
         * Checks for most popular case of regular, horizontal text
         */
        public boolean isHorizontal()
        {
        	return Math.abs(getBase().get(Vector.I2)) < 0.0001;
        }

        public boolean isVertical()
        {

        	return Math.abs(getBase().get(Vector.I1)) < 0.0001;
        }

        // Comparison tool, gives 0 for zero, -1 for negatives and 1 for positives
        private /*static*/ int cmp(double v) {
        	if(Math.abs(v) < 0.001) // NOTE: adjust tolerance for 0
        		return 0;
        	return (v < 0) ? -1 : 1;
        }

        /*
         * Compares text direction of two glyphs (0=same direction)
         */
        public int cmpDir(CharPos c) {
        	final double dir = VectorUtils.crossProduct(getBase(), c.getBase());
        	return cmp(dir);
        }

        /*
         * Compares vertical position of two glyphs (0=same line)
         */
        public int cmpLine(CharPos c) {
        	if (isHorizontal()) {
        		final float d = c.getOrigin().get(Vector.I2) - getOrigin().get(Vector.I2); // simple Y2 - Y1 for horizontal text
            	return (Math.abs(d) < LINE_TOL) ? 0 : cmp(d);
        	}
        	Vector p = c.getOrigin().subtract(getOrigin());
        	final float l0 = getBase().length();
        	Vector pp = getBase().multiply(p.dot(getBase()) / (l0 * l0));
        	Vector d = p.subtract(pp);
        	if (Math.abs(d.length()) < LINE_TOL)
        		return 0;
        	return cmp(VectorUtils.crossProduct(getBase(), d));
        }

        /*
         * Compares horizontal position of two glyphs
         */
        public int cmpOrder(CharPos c) {
        	if (isHorizontal())
        		return cmp(getOrigin().get(Vector.I1) - c.getOrigin().get(Vector.I1));
        	final double x = -getBase().dot(c.getOrigin().subtract(getOrigin()));
        	return cmp(x);
        }

        public String toString() {
            return "\"" + c + "\":(" + origin.get(Vector.I1) + "," + origin.get(Vector.I2) + "),<" + base.get(Vector.I1) + ","+ base.get(Vector.I2) + ")";
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

            if( !text.equals(" ") && !text.equals("\t") &&
                !text.equals("\n")  && !text.equals("\r") &&
                !text.equals("\u00A0")) {

                containsGlyphs = true;
                int codePoint = text.codePointAt(0);
                // 0x20000 marks beginning of unassigned planes,
                // 0xE0000 and 0xF0000 are special purpose planes, no
                // useful glyphs in that range.
                containsControlCodes = containsControlCodes || codePoint<32 || codePoint>=0x20000;

                CharPos cp = new CharPos(text, tri.getBaseline());
                if( cp.isHorizontal() || cp.isVertical()) {
                    allCharacters.add(cp);
                }
            }
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

        double tmp = 0;
        if( l>r ) {
            tmp = l; l = r; r = tmp;
        }
        if( b>t ) {
            tmp = b; b = t; t = tmp;
        }
        foundText = new ArrayList<String>();

        int i;
        CharPos last = null;
        for( i=0; i<allCharacters.size(); i++ ) {
            CharPos cp = allCharacters.get(i);
            // check if the middle of baseline
            final double xm = cp.getOrigin().get(Vector.I1) + cp.getBase().get(Vector.I1)/2;
            final double ym = cp.getOrigin().get(Vector.I2) + cp.getBase().get(Vector.I2)/2;
            if((xm >= l) && (xm <= r) && (((ym >= b) && (ym <= t)) || ((ym >= t) && (ym <= b)))
            	&& cp.c.codePointAt(0)>=32 ) {

                String txt = cp.c;

                if( last==null || (0 != last.cmpDir(cp)) || (0 != last.cmpLine(cp))) {
                    foundText.add("");
                }
                else if( cp.getOrigin().subtract(last.getEndPoint()).length() > 0.1 * (cp.getWidth() + last.getWidth()) ) {
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
    		final int dir = cp1.cmpDir(cp2);
    		if(dir != 0)
    			return dir;
    		final int cy = cp1.cmpLine(cp2);
    		return (cy != 0) ? cy : cp1.cmpOrder(cp2);
        }
    }
    
    Vector getTextDir()
    {
    	class Dir implements Comparable<Dir> {
        	private static final float SCALE = 1000.0f;
    		private int x, y;
    		Dir(Vector v) {
            	v = v.normalize();
    			x = (int)(v.get(Vector.I1) * SCALE + 0.5);
    			y = (int)(v.get(Vector.I2) * SCALE + 0.5);
    		}
    		public int compareTo(Dir o) {
    			return (y == o.y) ? x - o.x : y - o.y;
    		}
    		Vector toVector() {
    			return new Vector(x / SCALE, y / SCALE, 0.0f);
    		}
    	}
    	int kh = 0, kv = 0, ks = 0;
    	Map<Dir, Integer> map = new TreeMap<Dir, Integer>();
        for( CharPos i: allCharacters ) {
        	Vector v = i.getBase();
        	if (i.isHorizontal() && (v.get(Vector.I1) > 0))
        		++kh; // horizontal text
        	else if (i.isVertical() && (v.get(Vector.I2) > 0))
        		++kv; // vertical text
        	else {
        		final Dir d = new Dir(v);
        		final Integer c = map.get(d), c1 = 1 + ((c == null) ? 0 : c);
        		map.put(d, c1);
        		if (c1 > ks)
        			ks = c1;
        	}
        }
        if ((kh > kv) && (kh > ks)) // horizontal text dominates
       		return new Vector(1, 0, 0);
       if ((kv > kh) && (kv > ks)) // vertical text dominates
       		return new Vector(0, 1, 0);

       // Select dominating skewed text direction
        Dir best = null;
        int k = -1, k1 = -1;
        for( Dir d: map.keySet() ) {
        	final int c = map.get(d);
        	if ((best == null) || (c > k)) {
        		k1 = k; // keep second to most popular direction
        		best = d; k = c;
        	}
        }
        if ((best == null ) || (k == k1)) // check for two or more directions with same number of glyphs
        	return null;
        else
        	return best.toVector();
    }

    int detectRotate()
    {
    	final Vector dir = getTextDir();
    	if (dir == null)
    		return 0;
    	return (int)Math.round(-Math.atan2(dir.get(Vector.I2), dir.get(Vector.I1)) * 180 / Math.PI);
    }

};

public class ExtractTexts {
    public static void execute(String specFile, String inputOverride, String outputOverride)
        throws IOException, DocumentException
    {
        ExtractTextSpec spec = ExtractTextSpec.loadFromFile(specFile);
        if( inputOverride!=null ) {
            spec.input = inputOverride;
        }
        if( outputOverride!=null ) {
            spec.stampedOutput = outputOverride;
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
    
    /**
     * Returns auto page rotation given by dominant text direction 
     * 
     * @return 0/90/180/270 int
     */
    public static int detectPageRotation(PdfReader reader, int iPage) throws IOException {
        ExtractTextsRenderListener chars = new ExtractTextsRenderListener();
        PdfReaderContentParser parser = new PdfReaderContentParser(reader);
        parser.processContent(iPage, chars);
        chars.finalizeSearch();
        return -chars.detectRotate();
    }

    public static Rectangle getRectInRotatedCropBoxCoordinates(PdfReader reader, int page, ArrayList<Double> rect) {
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
        Rectangle crop = reader.getPageSize(page);

        Rectangle origrect;
        origrect = new Rectangle((float)(double)rect.get(0), (float)(1-rect.get(1)), (float)(double)rect.get(2), (float)(1-rect.get(3)));

        double l = 0, t = 0, r = 0, b = 0;
        int rotate = reader.getPageRotation(page);
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

                    Rectangle frame = getRectInRotatedCropBoxCoordinates(reader, rect.page, rect.rect);
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

    public static void execute(ExtractTextSpec spec)
        throws IOException, DocumentException
    {
        /* This is here to flatten forms so that texts in them can be read
         */
        PdfReader reader = new PdfReader(spec.input);
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
                    spec.additionalInfo.containsControlCodes = spec.additionalInfo.containsControlCodes || rl.containsControlCodes;
                    spec.additionalInfo.containsGlyphs = spec.additionalInfo.containsGlyphs || rl.containsGlyphs;
                }

                Rectangle frame = getRectInRotatedCropBoxCoordinates(reader, rect.page, rect.rect);
                double l = frame.getLeft();
                double t = frame.getTop();
                double r = frame.getRight();
                double b = frame.getBottom();
                rl.find(l,b,r,t);

                rect.lines = new ArrayList<String>();
                for( String line : rl.foundText ) {
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
