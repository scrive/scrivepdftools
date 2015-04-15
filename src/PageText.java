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
        return VectorUtils.crossProduct(getBase(), d);
    }

    /*
     * Compares horizontal position of two glyphs
     */
    public float cmpOrder(CharPos c) {
        if (isHorizontal())
            return (bx >= 0.0f) ? (c.getX() - x0) : (x0 - c.getX()); // simple X2 - X1 for horizontal text
        else if (isVertical())
            return (by > 0.0f) ? (c.getY() - y0) : (y0 - c.getY()); // simple Y2 - Y1 for vertical text
        
        return -getBase().dot(new Vector(c.getX() - x0, c.getY() - y0, 0.0f));
    }

    public boolean detectSpace(CharPos prev) {
        final float d0 = 0.1f * (getWidth() + prev.getWidth());
        final float dx = getX() - prev.getX2(), dy = getY() - prev.getY2();   
        return dx * dx + dy * dy > d0 * d0;
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

    /*
     *  Maximum vertical distance between two glyphs that can be assigned to the same line
     */
    public static final float LINE_TOL = 4.0f;

    // Comparison tool, gives 0 for zero, -1 for negatives and 1 for positives
    private /*static*/ int cmp(float v, float tol) {
        if (Math.abs(v) < tol) // NOTE: adjust tolerance for 0
            return 0;
        return (v < 0.0) ? -1 : 1;
    }
    
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
                String text = tri.getText().replaceAll("( |\t|\r|\n|\u00A0)", "");
                if( !text.isEmpty() ) {
                    pageText.addChar(text, tri.getBaseline());
                }
            }
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
        fixPoorMansBold();

//        for (Map.Entry<Integer, ArrayList<ArrayList<CharPos>>> all: lines.entrySet()) {
//            System.out.println("Text (rot = " + (double)(all.getKey() / 1000.0) + "):");
//            for (ArrayList<CharPos> line: all.getValue()) {
//                String txt = "";
//                for (CharPos cp: line)
//                    txt += cp.c;
//                System.out.println("\t(" + line.get(0).getX() + ", " + line.get(0).getY() + "): " + txt);
//            }
//        }

        // geometry
        pageRotation = reader.getPageRotation(iPage);
        Rectangle r = reader.getCropBox(iPage);
        pageSize = new Rect(r.getLeft(), r.getBottom(), r.getWidth(), r.getHeight());
        for ( int rot = pageRotation; rot > 0; rot -= 90)
            r = r.rotate();
        pageSizeRotated = new Rect(r.getLeft(), r.getBottom(), r.getWidth(), r.getHeight());
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
            p = cmp(lines.get(i).get(0).cmpLine(cp), LINE_TOL);
        }
        if (p == 0) {
            best = lines.get(i - 1);
        } else {
            best = new ArrayList<CharPos>();
            lines.add((p < 0) ? i - 1 : i, best);
        }
        // select char
        p = 1;
        for (i = 0; (i < best.size()) && (p > 0); ++i) {
            p = cmp(best.get(i).cmpOrder(cp), 0.0f);
        }
        best.add((p < 0) ? i - 1 : i, cp);

        return true;
    }
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
        if( cp.isHorizontal() || cp.isVertical() )
            addChar(cp);
    }

    // detect duplicated glyphs (poor man's bold)
    private void fixPoorMansBold() {        
        for (Map.Entry<Integer, ArrayList<ArrayList<CharPos>>> all: lines.entrySet())
            for (ArrayList<CharPos> line: all.getValue()) {
                CharPos prev = null;
                for (int i = 0; i < line.size(); ) {
                    if ((prev != null) && (prev.c.equals(line.get(i).c)) && (0.5 < line.get(i).covers(prev))) {
                        line.remove(i);                   
                    } else {
                        prev = line.get(i);
                        ++i;
                    }
                }
            }
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
