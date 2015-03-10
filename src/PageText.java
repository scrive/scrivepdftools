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
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.ImageRenderInfo;
import com.itextpdf.text.pdf.parser.LineSegment;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;
import com.itextpdf.text.pdf.parser.RenderListener;
import com.itextpdf.text.pdf.parser.TextRenderInfo;
import com.itextpdf.text.pdf.parser.Vector;

class VectorUtils {

//  public static Vector add(Vector v1, Vector v2) {
//      return new Vector(v1.get(Vector.I1) + v2.get(Vector.I1), v1.get(Vector.I2) + v2.get(Vector.I2), v1.get(Vector.I3) + v2.get(Vector.I3));
//  }

    public static float crossProduct(Vector v1, Vector v2) {
        return v1.get(Vector.I1) * v2.get(Vector.I2) - v1.get(Vector.I2) * v2.get(Vector.I1);
    }

}

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
//    private Vector origin;
//    private Vector base; // baseline = glyph direction + length

    /*
     *  Maximum vertical distance between two glyphs that can be assigned to the same line
     */
    public static final float LINE_TOL = 4.0f;

    public CharPos(String text, LineSegment base)
    {
        c = text;
        // save base line
        Vector origin = base.getStartPoint();
        x0 = origin.get(Vector.I1);
        y0 = origin.get(Vector.I2);
        Vector b = base.getEndPoint().subtract(origin);
        bx = b.get(Vector.I1);
        by = b.get(Vector.I2);
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
    private /*static*/ int cmp(float v) {
        if (Math.abs(v) < 0.001) // NOTE: adjust tolerance for 0
            return 0;
        return (v < 0.0) ? -1 : 1;
    }

    /*
     * Compares text direction of two glyphs (0=same direction)
     */
    public int cmpDir(CharPos c) {
        return cmp(x0 * c.getY() - y0 * c.getX());
    }

    /*
     * Compares vertical position of two glyphs (0=same line)
     */
    public int cmpLine(CharPos c) {
        if (isHorizontal()) {
            final float d = c.getY() - y0; // simple Y2 - Y1 for horizontal text
//            if ((Math.abs(d) > 0) && (Math.abs(d) < LINE_TOL))
//                return 0;
            return cmp(d);
//          return (Math.abs(d) < LINE_TOL) ? 0 : cmp(d);
        }
        Vector p = new Vector(c.getX() - x0, c.getY() - y0, 0.0f);
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
            return cmp(x0 - c.getX());
        final float x = -getBase().dot(new Vector(c.getX() - x0, c.getY() - y0, 0.0f));
        return cmp(x);
    }

    /*
     * Checks if two characters occupy the same location. Returns cover length % (0-100)
     */
    public float covers(CharPos c) {
        float lc = 0; // cover length
        if (isHorizontal() && c.isHorizontal()) { // horizontal special case (perf optimized)
            final float x0 = getX(), x1 = c.getX();
            final float b0 = getX2(), b1 = c.getX2();
            if (((x0 < x1) && (b0 <= x1)) || ((x1 < x0) && (b1 <= x0)))
                return 0.0f;
            lc = ((x0 < x1) ? (b0 - x1) : (b1 - x0)); 
        } else {
            final Vector v1 = new Vector(c.getX() - getX2(), c.getY() - getY2(), 0.0f);
            final Vector v2 = new Vector(getX() - c.getX2(), getY() - c.getY2(), 0.0f);
            if (Math.signum(v1.dot(getBase())) != Math.signum(v2.dot(c.getBase())))
                return 0.0f;
            lc = Math.min(v1.length(), v2.length());
        }
        return lc / Math.min(getWidth(), c.getWidth()); 
    }

    public String toString() {
        return "\"" + c + "\":(" + x0 + "," + y0 + "),<" + bx + ","+ by + ")";
    }
};

public class PageText implements Serializable
{
    private static final long serialVersionUID = -7658415439714179901L;

    /**
     * Plain 2D rectangle
     * @author wizzard
     *
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

    private class CharPosComparator implements Comparator<CharPos> {

        @Override
        public int compare(CharPos cp1, CharPos cp2) {
            final int dir = cp1.cmpDir(cp2);
            if(dir != 0)
                return dir;
            final int cy = cp1.cmpLine(cp2);
            return (cy != 0) ? cy : cp1.cmpOrder(cp2);
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
                String text = tri.getText();
                if( !text.equals(" ") && !text.equals("\t") &&
                    !text.equals("\n")  && !text.equals("\r") &&
                    !text.equals("\u00A0")) {
                    pageText.addChar(text, tri.getBaseline());
                }
            }
        }
    }

    Rect pageSize = null;
    Rect pageSizeRotated = null;
    int pageRotation = 0;
    private ArrayList<CharPos> allChars = new ArrayList<CharPos>();
    private boolean hasGlyphs = false;
    private boolean hasControlCodes = false;

    public PageText(PdfReader reader, int iPage) throws IOException {
        PdfReaderContentParser parser = new PdfReaderContentParser(reader);
        parser.processContent(iPage, new PageTextRenderListener(this));
        Collections.sort(allChars, new CharPosComparator());
        // geometry
        pageRotation = reader.getPageRotation(iPage);
        Rectangle r = reader.getPageSize(iPage);
        pageSize = new Rect(r.getLeft(), r.getBottom(), r.getWidth(), r.getHeight());
        r = reader.getPageSizeWithRotation(iPage);
        pageSizeRotated = new Rect(r.getLeft(), r.getBottom(), r.getWidth(), r.getHeight());
    }

    public boolean containsGlyphs() { return hasGlyphs; }
    public boolean containsControlCodes() { return hasControlCodes; }

    /**
     * IMPORTANT: filters skewed text (non-vertical and non-horizontal)
     */
    public void addChar(String c, LineSegment base) {

        hasGlyphs = true;
        int codePoint = c.codePointAt(0);
        // 0x20000 marks beginning of unassigned planes,
        // 0xE0000 and 0xF0000 are special purpose planes, no
        // useful glyphs in that range.
        hasControlCodes = hasControlCodes || codePoint<32 || codePoint>=0x20000;
        
        CharPos cp = new CharPos(c, base);
        if( cp.isHorizontal() || cp.isVertical()) {
            allChars.add(cp);
        }

    }

    public Vector getTextDir()
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
        for( CharPos i: allChars ) {
            if (i.isHorizontal() && (i.getBaseX() > 0.0f))
                ++kh; // horizontal text
            else if (i.isVertical() && (i.getBaseY() > 0.0f))
                ++kv; // vertical text
            else {
                final Dir d = new Dir(i.getBase());
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

    public ArrayList<CharPos> getChars() {
        return allChars;
    }
}
