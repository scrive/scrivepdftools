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
import com.itextpdf.text.DocumentException;

class Main
{
    public static void main(String[] args)
        throws IOException, DocumentException
    {
        if( args.length!=2) {
            System.err.println("Usage:");
            System.err.println("    java -jar scrivepdftools.jar add-verification-pages config.json");
            System.err.println("    java -jar scrivepdftools.jar find-texts config.json");
            System.err.println("    java -jar scrivepdftools.jar extract-texts config.json");
            System.err.println("");
            System.err.println("scrivepdftools uses the following products:");
            System.err.println("   iText by Bruno Lowagie, iText Group NV ");
            System.err.println("   snakeyaml");

        }
        else {
            if( args[0].equals("add-verification-pages")) {
                AddVerificationPages.execute(args[1]);
            }
            else if( args[0].equals("find-texts")) {
                FindTexts.execute(args[1]);
            }
            else if( args[0].equals("extract-texts")) {
                ExtractTexts.execute(args[1]);
            }
            else {
                System.err.println("Uknown verb " + args[0]);
            }
        }
    }
}
