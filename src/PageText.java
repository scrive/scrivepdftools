/*
 *  Copyright (C) 2015 Scrive AB
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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import com.itextpdf.awt.geom.Rectangle2D;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.ImageRenderInfo;
import com.itextpdf.text.pdf.parser.LineSegment;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;
import com.itextpdf.text.pdf.parser.RenderListener;
import com.itextpdf.text.pdf.parser.TextRenderInfo;
import com.itextpdf.text.pdf.parser.Vector;

/**
 * Responsible for text processing and storage.
 *
 */
class CharPos implements Serializable
{
    private static final long serialVersionUID = 6875877776978057339L;
    
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
    private float x0, y0, bx, by;
    private Rectangle2D bbox = null, bbox2 = null;

    public CharPos(String text, LineSegment base, Rectangle2D bbox)
    {
        c = text;
        this.bbox = bbox;
        // save base line
        Vector origin = base.getStartPoint();
        x0 = origin.get(Vector.I1);
        y0 = origin.get(Vector.I2);
        Vector b = base.getEndPoint().subtract(origin);
        bx = b.get(Vector.I1);
        by = b.get(Vector.I2);
        final float xc = x0 + 0.5f * bx, yc = y0 + 0.5f * by; // base line center
        bbox2 = new Rectangle2D.Float(xc, yc, 0.0f, 0.0f); 
    }

    public float getX() {
        return x0;
    }
    public float getY() {
        return y0;
    }
    public float getBaseX() {
        return bx;
    }
    public float getBaseY() {
        return by;
    }
    public Vector getBase()   {
        return new Vector(bx, by, 0.0f);
    }
    public float getX2() {
        return x0 + bx;
    }
    public float getY2() {
        return y0 + by;
    }

    public Rectangle2D getBounds() {
        return bbox;
    }

    public Rectangle2D getBaseLineBounds() {
        return bbox2;
    }

    /*
     * Returns glyph's width
     */
    public float getWidth() {
        if (isHorizontal())
            return Math.abs(bx);
        else if (isVertical())
            return Math.abs(by);
        else
            return getBase().length();
    }

    /*
     * Checks for most popular case of regular, horizontal text
     */
    public boolean isHorizontal()
    {
        return Math.abs(by) < 0.0001;
    }

    public boolean isVertical()
    {
        return Math.abs(bx) < 0.0001;
    }

    // Comparison tool, gives 0 for zero, -1 for negatives and 1 for positives
    public static int cmp(float v, float tol) {
        if (Math.abs(v) < tol) // NOTE: adjust tolerance for 0
            return 0;
        return (v < 0.0) ? -1 : 1;
    }
    
    /*
     * Compares text direction of two glyphs (0=same direction)
     */
    public float cmpDir(CharPos c) {        
        return bx * c.getBaseY() - by * c.getBaseX();
    }

    /*
     * Compares vertical position of two glyphs (0=same line)
     */
    public float cmpLine(CharPos c) {
        if (isHorizontal())
            return (bx >= 0.0f) ? (y0 - c.getY()) : (c.getY() - y0); // simple Y2 - Y1 for horizontal text
        else if (isVertical())
            return (by > 0.0f) ? (c.getX() - x0) : (x0 - c.getX()); // simple X2 - X1 for vertical text

        Vector p = new Vector(c.getX() - x0, c.getY() - y0, 0.0f);
        final float l0 = getBase().length();
        Vector pp = getBase().multiply(p.dot(getBase()) / (l0 * l0));
        Vector d = p.subtract(pp);
        return bx * d.get(Vector.I2) - by * d.get(Vector.I1); 
    }

    /*
     * Compares horizontal position of two glyphs
     */
    public int cmpOrder(CharPos c2) {
        final float cx = (float)c2.getBounds().getCenterX();
        final float cy = (float)c2.getBounds().getCenterY();
        if (isHorizontal()) {
            final int z = (cx >= bbox.getMaxX()) ? 1 : ((cx <= bbox.getMinX()) ? -1 : 0);
            return (bx >= 0.0f) ? z : -z;
        } else if (isVertical()) {
            final int z = (cy >= bbox.getMaxY()) ? 1 : ((cy <= bbox.getMinY()) ? -1 : 0);
            return (by >= 0.0f) ? z : -z;
        }
        
        return cmp(getBase().dot(new Vector(c2.getX() - x0, c2.getY() - y0, 0.0f)), 0.0f);
    }

    public boolean detectSpace(CharPos prev) {
        final float space = 0.2f * getWidth();
        if ((space == 0) && (bbox.getX() >= prev.getBounds().getMinX()) && (bbox.getX() <= prev.getBounds().getMaxX()) && (bbox.getY() >= prev.getBounds().getMinY()) && (bbox.getY() <= prev.getBounds().getMaxY())) 
            return false; // 0-width problem (sadly we need own version of 'contains' for empty boxes...) 
        if (isHorizontal()) {
            return (bx >= 0.0f) ? (bbox.getMinX() >= prev.getBounds().getMaxX() + space) : (bbox.getMaxX() <= prev.getBounds().getMinX() - space);   
        } else if (isVertical()) {
            return (by >= 0.0f) ? (bbox.getMinY() >= prev.getBounds().getMaxY() + space) : (bbox.getMaxY() <= prev.getBounds().getMinY() - space);   
        }

        final Vector b = prev.getBase();
        final Vector b2 = new Vector(getX() - prev.getX(), getY() - prev.getY(), 0.0f);
        final float l = b.length(), l2 = b.dot(b2) / l;
        return l2 >= l + space; 
    }
    
    public void merge(CharPos other) {
        c += other.c;
        if (!isVertical())
            bx = Math.max(bx, other.getX2() - x0);
        if (!isHorizontal())
            by = Math.max(by, other.getY2() - y0);
        bbox.add(other.bbox);
        bbox2.add(other.bbox2);
    }
    
    public String toString() {
        return "\"" + c + "\":(" + x0 + "," + y0 + "),<" + bx + ","+ by + ">";
    }
};

public class PageText implements Serializable
{
    private static final long serialVersionUID = -7658415439714179901L;

    /**
     * Regex for whitespace detection
     */
    public static final String WHITE_SPACE = "[ \t\n\u000B\f\r\u00A0\uFEFF\u200B]";

    /*
     *  Maximum vertical distance between two glyphs that can be assigned to the same line
     */
    public static final float LINE_TOL = 4.0f;

    /**
     * Plain 2D rectangle
     */
    public class Rect implements Serializable {
        private static final long serialVersionUID = -1267272873159187043L;
        private float x, y, w, h;
        
        public Rect(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
        
        public float getLeft() {
            return x;
        }
        public float getBottom() {
            return y;
        }
        public float getWidth() {
            return w;
        }
        public float getHeight() {
            return h;
        }
    }

    private class PageTextRenderListener implements RenderListener
    {
        PageText pageText;

        PageTextRenderListener(PageText text) {
            pageText = text;
            
        }
        
        public void beginTextBlock() {
        }
        public void endTextBlock() {
        }
        public void renderImage(ImageRenderInfo renderInfo) {
        }
        public void renderText(TextRenderInfo renderInfo) {
            for( TextRenderInfo tri: renderInfo.getCharacterRenderInfos() ) {
                String text = tri.getText().replaceAll(WHITE_SPACE, "");
                if( !text.isEmpty() ) {
                    LineSegment base = tri.getBaseline();
                    if (base.getLength() == 0.0f) { // 0-width problem: can't access ctm and text matrix from GS to deduce text direction !
                        //System.err.println("Error: 0-width text glyphs");
                    }
                    final Rectangle2D r0 = base.getBoundingRectange();
                    final Rectangle2D r1 = tri.getAscentLine().getBoundingRectange();
                    final Rectangle2D r2 = tri.getDescentLine().getBoundingRectange();
                    pageText.addChar(text, base, r1.createUnion(r2).createUnion(r0));
                }
            }
        }
    }

    enum X {
        
    }
    static class BiDiText {
        /** The table below encodes simplified Unicode BiDi classes (see Unicode Standard Annex #44, table 13)
         * each entry is (i<<3)+c, where i is starting Unicode character code/index and c is a simplified 0-4 BiDi class: 
         */
        public static int NEUTRAL  = 0;
        public static int WEAK     = 1;
        public static int EXPLICIT = 2;
        public static int LEFT     = 3;
        public static int RIGHT    = 4;

        private static int[] bidiClass = {1,72,113,224,281,304,345,472,523,728,779,984,1017,1064,1073,1288,1297,1328,1363,1368,1385,1392,1409,1440,1451,1456,1481,1491,1496,1539,1720,1731,1976,1987,5576,5595,5648,5763,5776,5891,
            5928,6003,6008,6145,7043,7072,7091,7152,7163,7200,7219,7224,7235,8112,8123,9241,9299,11344,11385,11764,11769,11780,11785,11804,11809,11828,11833,11908,12289,12336,12356,12361,12380,12385,
            12396,12400,12417,12508,12889,13164,13185,13196,14001,14064,14073,14124,14137,14152,14161,14196,14209,14292,14473,14484,14721,14956,15665,15756,16217,16292,16304,16340,16561,16596,16601,
            16676,16681,16708,16713,16772,17097,17140,18209,18459,18897,18907,18913,18923,18953,19019,19049,19059,19081,19139,19217,19235,19465,19475,19937,19947,19977,20027,20073,20083,20241,20275,
            20369,20387,20441,20507,20961,20979,21001,21195,21377,21395,21417,21531,21985,21995,22025,22091,22121,22147,22289,22323,22409,22547,23009,23019,23033,23043,23049,23099,23145,23227,23313,
            23347,23569,23579,24065,24075,24169,24195,24472,24521,24528,24577,24587,25073,25099,25137,25283,25361,25395,25536,25595,25609,25619,26081,26091,26209,26283,26385,26419,26633,26643,27145,
            27187,27241,27251,27409,27443,28241,28283,28305,28355,29065,29075,29089,29187,29241,29307,30089,30099,30113,30187,30273,30339,30913,30931,31145,31155,31161,31171,31177,31184,31219,31625,
            31739,31745,31787,31793,31811,31849,32243,32305,32315,33129,33163,33169,33219,33225,33243,33257,33275,33473,33491,33521,33547,33673,33707,33809,33819,33833,33851,33897,33907,34025,34035,
            39657,39683,40064,40195,40960,40971,46080,46091,46296,46339,47249,47363,47505,47531,47761,47875,48017,48131,48545,48563,48569,48627,48689,48699,48713,48803,48857,48867,48873,48899,49024,
            49241,49283,50505,50515,51457,51483,51513,51531,51601,51611,51657,51712,51763,52976,53251,53433,53451,53465,53491,53937,53947,53953,54027,54033,54043,54057,54123,54169,54275,54657,55331,
            55713,55723,55729,55771,55777,55787,55825,55835,56153,56227,56321,56339,56593,56627,56641,56659,56665,56691,57137,57147,57153,57171,57193,57203,57209,57235,57697,57763,57777,57819,59009,
            59035,59041,59147,59153,59211,59241,59251,59297,59307,59329,59395,60929,61443,65000,65011,65016,65043,65128,65155,65256,65283,65384,65427,65512,65625,65651,65660,65664,65874,65913,65960,
            66081,66088,66305,66354,66385,66443,66465,66528,66555,66561,66656,66691,66817,67584,67603,67608,67643,67648,67667,67744,67755,67760,67787,67824,67875,67880,67891,67896,67907,67912,67923,
            67953,67963,68048,68067,68096,68139,68176,68211,68224,68355,68680,69777,69792,72115,72664,72875,72880,74817,74979,75600,79203,79208,81923,83968,90115,91944,91995,92025,92051,92104,92163,
            93177,93187,93953,94208,98347,98368,98571,98641,98675,98688,98699,98736,98755,98792,98827,99529,99544,99563,99584,99595,100312,100323,101888,102275,102632,102659,103040,103171,103392,
            103419,103816,103939,104032,104067,105400,105435,106224,106243,106488,106499,159232,159747,337024,337539,340072,340099,340857,340888,340897,340976,340995,341241,341251,341889,341907,342016,
            342291,343104,343115,344081,344091,344113,344123,344153,344163,344361,344379,344384,344451,344513,344579,344992,345091,345633,345715,345857,346003,346417,346483,346681,346771,347137,347163,
            347545,347555,347569,347603,347617,347627,347945,347955,348489,348539,348553,348571,348585,348675,348697,348707,348769,348779,349153,349163,349569,349579,349585,349611,349625,349643,349681,
            349699,349705,349715,350049,350067,350129,350219,352041,352051,352065,352075,352105,352131,514284,514289,514300,514377,514388,518640,518788,520168,520193,520320,520449,520576,520833,520840,
            520849,520864,520873,520880,520953,520960,520977,520992,521033,521048,521092,522233,522248,522265,522288,522329,522456,522507,522712,522763,522968,523059,524033,524048,524073,524096,524291,
            526344,526355,526848,528003,528361,529411,530177,530435,531377,531459,540676,542968,542980,544777,544900,545217,545284,546601,546652,547272,547332,553729,557059,557065,557075,557505,557627,
            557712,557875,558073,558099,558489,558523,558537,558555,559105,559131,559417,559459,559465,559539,560025,560035,560129,560147,560561,560635,561529,561555,561569,561579,561585,561603,562937,
            562947,562969,563075,563209,563219,563681,563691,563713,563723,564017,566275,566681,566731,566737,566747,566777,566795,566801,566819,568721,568771,568801,568819,568825,568843,569753,569819,
            569833,569843,569849,569867,570713,570723,570729,570739,570753,570803,570809,570883,743297,743339,743809,743867,752761,752795,910569,910587,910593,950275,953145,953171,953241,953371,953385,
            953443,953681,953715,954368,954897,954920,957187,964312,964323,964776,964787,965240,965251,965704,965715,966168,966179,966257,999428,1001089,1011716,1013632,1017857,1017944,1017987,1018704,
            1018755,1021952,1048579,7340041,7864323};
        private static int bidiClassLast = bidiClass[bidiClass.length - 1];
        private static String fastBiDiClass = ",,,,+##,,,,,,,###$,##$,,,,,,,+##&>>>>>>>>>>>>;##&>>>>>>>>>>>>;#$,,+,,,,,,,,,,,,,+,,##;$#,,&#$;##>>>>>>>>>>>;>>>>>>>>>>>>>>>;>>>>";

        private static int getBiDiClass(char c) {
            if (c < fastBiDiClass.length() * 2) {
                final int v = fastBiDiClass.charAt(c >> 1) - 35;
                return ((c & 1) == 1) ? (v & 7) : (v >> 3);
            }
            if (c >= bidiClassLast >> 3)
                return bidiClassLast & 7;
            int i = Arrays.binarySearch(bidiClass, (int)(c << 3));
            if (i < 0)
                i = -i - 1;
            while (c < (bidiClass[i] >> 3))
                --i;
            return bidiClass[i] & 7;
        }

        private StringBuffer buf = new StringBuffer(), rev = null, weak = null;

        private BiDiText() {
        }

        // finish weak sequence within RTL
        private void closeWeak() {
            if (weak == null)
                return;
            rev.append(weak.reverse());
            weak = null;
        }

        // finish RTL sequence
        private void closeRTL() {
            if (rev == null)
                return;
            buf.append(rev.reverse());
            rev = null;
        }

        private String fix(String str) {
            final int l = str.length();
            for (int i = 0; i < l; ++i) {
                final char ci = str.charAt(i);
                final int bidi = getBiDiClass(ci);
                if (bidi == RIGHT) {
                    if (rev == null) // start RTL sequence 
                        rev = new StringBuffer(l - i);
                    else
                        closeWeak();
                    rev.append(ci);
                } else if (rev != null) {
                    if (WEAK == bidi) {
                        if (weak == null) // start weak sequence within RTL 
                            weak = new StringBuffer(l - i);
                        weak.append(ci);
                    } else if (NEUTRAL == bidi) {
                        closeWeak();
                        rev.append(ci); // continue RTL
                    } else if (LEFT == bidi) {
                        closeWeak();
                        closeRTL();
                        buf.append(ci);
                    }
                } else {
                    if (weak != null)
                        throw new RuntimeException("Wooo !");
                    if (rev != null)
                        throw new RuntimeException("Heee !");
                    buf.append(ci);
                }
            }
            closeWeak();
            closeRTL();
            return buf.toString();
        }

    public static String fixBiDiText(String str) {
//            for (int i = 0; i < 256; ++i)
//                System.out.println("BiDiClass[" + i + "] = " + getBiDiClass((char)i));
            boolean any = false;
            final int l = str.length();
            for (int i = 0; i < l; ++i)
                if (getBiDiClass(str.charAt(i)) == RIGHT) {
                    any = true;
                    break;
                }
            if (!any) // just return original string if no changes are required
                return str;
            return (new BiDiText()).fix(str);
        }
    }

    Rect pageSize = null;
    Rect pageSizeRotated = null;
    int pageRotation = 0;
    private boolean hasGlyphs = false;
    private boolean hasControlCodes = false;
    private TreeMap<Integer, ArrayList<ArrayList<CharPos>>> lines = new TreeMap<Integer, ArrayList<ArrayList<CharPos>>>();

    public PageText(PdfReader reader, int iPage) throws IOException {
        PdfReaderContentParser parser = new PdfReaderContentParser(reader);
        parser.processContent(iPage, new PageTextRenderListener(this));

        // geometry
        pageRotation = reader.getPageRotation(iPage);
        Rectangle r = reader.getCropBox(iPage);
        pageSize = new Rect(r.getLeft(), r.getBottom(), r.getWidth(), r.getHeight());
        for ( int rot = pageRotation; rot > 0; rot -= 90)
            r = r.rotate();
        pageSizeRotated = new Rect(r.getLeft(), r.getBottom(), r.getWidth(), r.getHeight());
    }

    public static String line2str(ArrayList<CharPos> line) {
        String str = "(" + line.get(0).getX() + ", " + line.get(0).getY() + "): ";
        for (CharPos cp: line)
            str += cp.c + " ";
        return str;
    }
    
    public String toString() {
        String dump = "";
        for (Map.Entry<Integer, ArrayList<ArrayList<CharPos>>> all: lines.entrySet()) {
            dump += "Text (rot = " + (int)(all.getKey() * 180.0 / Math.PI / 1000.0) + "°):\n";
            for (int i = 0; i < all.getValue().size(); ++i)
                dump += "\t" + i + ": " + line2str(all.getValue().get(i)) + "\n";
        }
        return dump;
    }

    public boolean containsGlyphs() { return hasGlyphs; }
    public boolean containsControlCodes() { return hasControlCodes; }

    private boolean addChar(CharPos cp) {
        // select direction
        final int dir = (int)(1000.0 * Math.atan2(cp.getBaseY(), cp.getBaseX()) + 0.5);
        ArrayList<ArrayList<CharPos>> lines = this.lines.get(dir);
        if (lines == null) {
            lines = new ArrayList<ArrayList<CharPos>>();
            this.lines.put(dir, lines);
        }

        // select line
        ArrayList<CharPos> best = null;
        int i = 0, p = 1;
        for (; (i < lines.size()) && (p > 0); ++i) {
            final CharPos c1 = lines.get(i).get(0);
            final float h = (float)(c1.isHorizontal() ? c1.getBounds().getHeight() : c1.getBounds().getWidth()); 
            final float tol = 0.36f * h; // % H, experimental number (recommended 0.36+)
            p = CharPos.cmp(c1.cmpLine(cp), tol);
        }
        if (p <= 0)
            --i;
        if (p == 0) {
            best = lines.get(i);
        } else {
            best = new ArrayList<CharPos>();
            lines.add(i, best);
            best.add(cp);
            return true;
        }

        // select char
        p = 1;
        for (i = 0; (i < best.size()) && (p > 0); ++i)
            p = best.get(i).cmpOrder(cp);
        if (0 == p)
            return false; // ignore overlapping glyphs (poor man's bold)
        if (p < 0)
            --i;
        final CharPos pred = (i > 0) ? best.get(i - 1) : null;
        if ((pred == null) || cp.detectSpace(pred)) 
            best.add(i, cp);
        else
            pred.merge(cp);
        return true;
    }
    /**
     * IMPORTANT: filters skewed text (non-vertical and non-horizontal)
     */
    public void addChar(String c, LineSegment base, Rectangle2D bbox) {

        hasGlyphs = true;
        int codePoint = c.codePointAt(0);
        // 0x20000 marks beginning of unassigned planes,
        // 0xE0000 and 0xF0000 are special purpose planes, no
        // useful glyphs in that range.
        hasControlCodes = hasControlCodes || codePoint<32 || codePoint>=0x20000;

        CharPos cp = new CharPos(c, base, bbox);
        if( cp.isHorizontal() || cp.isVertical() )
            addChar(cp);
    }

    public Vector getTextDir()
    {
        // select dominating direction
        int best = 0, c = 0, c2 = 0;
        for (Map.Entry<Integer, ArrayList<ArrayList<CharPos>>> l: lines.entrySet()) {
            int count = 0;
            for (ArrayList<CharPos> line: l.getValue())
                count += line.size();
            if (count > c) {
                best = l.getKey();
                c2 = c;
                c = count;
            }
        }
        if (c == c2) // two or more directions with same number of glyphs
            return null;

        double a = best / 1000.0;
        return new Vector((float)Math.cos(a), (float)Math.sin(a), 0);
    }

    /**
    * Returns auto page rotation given by dominant text direction 
    * 
    * @return 0/90/180/270 int
    */    
    int detectRotate()
    {
        final Vector dir = getTextDir();
        if (dir == null)
            return 0;
        return (int)Math.round(-Math.atan2(dir.get(Vector.I2), dir.get(Vector.I1)) * 180 / Math.PI);
    }

    public TreeMap<Integer, ArrayList<ArrayList<CharPos>>> getChars() {
        return lines;
    }
}
