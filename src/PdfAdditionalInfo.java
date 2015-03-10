import java.io.Serializable;

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

class PdfAdditionalInfo implements Serializable
{
    /**
     * 
     */
    private static final long serialVersionUID = -5173587408097084981L;

    /**
     * Number of pages.
     */
    public int numberOfPages = 0;

    /**
     * Printers have a setting to 'print as image'. If that is set all
     * semantic information is gone and whole pages are pure pixel
     * bits. It is useful to inform user about this error mode.
     *
     * True if PDF has any text rendering operators used anywhere.
     */
    public boolean containsGlyphs = false;

    /**
     * Some PDF generation software does not embed ToUnicode mapping
     * while at the same time using font subsets. Glyphs of letters
     * end up in code places lower than 32. Usually control chars are
     * not used, but if they were it is strong indicator that such
     * remapping has happened and glyphs cannot be matched to Unicode
     * code points anymore. Setting like 'do not subset' or 'embed
     * full font' may fix the issue depending on the PDF generator.
     *
     * Control code is a code in the range 0-31 except CR, LF and
     * TAB.
     */
    public boolean containsControlCodes = false;

    /**
     * First page Crop Box size in printer points. Useful to detect
     * differences between A4 pages and Letter Size pages. This is a
     * common error to change paper size.
     *
     * This is the way to detect portrait vs landscape errors.
     */
    public float firstPageWidth, firstPageHeight;
}
