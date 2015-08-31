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
import java.text.Bidi;
import java.text.Normalizer;
import java.util.ArrayList;
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

    public static String fixBiDiText(String text) {
        if ((text == null) || (text.length() == 0)) {
            return text;
        }
        Bidi bidi = new Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        if (bidi.isLeftToRight()) {
            return text;
        }
        final int count = bidi.getRunCount();
        final byte[] levels = new byte[count];
        final Integer[] runs = new Integer[count];
        for (int i = 0; i < count; i++) {
           levels[i] = (byte)bidi.getRunLevel(i);
           runs[i] = i;
        }
        Bidi.reorderVisually(levels, 0, runs, 0, count);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
           final int index = runs[i];
           final int start = bidi.getRunStart(index);
           int end = bidi.getRunLimit(index);
           if ((levels[index] & 1) != 0) {
             for (; --end >= start;) {
               result.append(text.charAt(end));
             }
           } else {
             result.append(text, start, end);
           }
        }
        return result.toString();
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
    public void addChar(String c1, LineSegment base, Rectangle2D bbox) {

        String c = Normalizer.normalize(c1, Normalizer.Form.NFC);
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
