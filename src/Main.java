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
    public static void execute(String command, String specFile, String inputOverride, String outputOverride)
    		throws IOException, DocumentException
    {
        if( command.equals("add-verification-pages")) {
            AddVerificationPages.execute(specFile, inputOverride, outputOverride);
        }
        else if( command.equals("find-texts")) {
            FindTexts.execute(specFile, inputOverride, outputOverride);
        }
        else if( command.equals("extract-texts")) {
            ExtractTexts.execute(specFile, inputOverride, outputOverride);
        }
        else if( command.equals("normalize")) {
            Normalize.execute(specFile, inputOverride, outputOverride);
        }
        else if( command.equals("select-and-clip")) {
            SelectAndClip.execute(specFile, inputOverride, outputOverride);
        }
        else {
            System.err.println("Uknown verb " + command);
        }
    }

    public static void main(String[] args)
        throws IOException, DocumentException
    {
        com.itextpdf.text.pdf.PdfReader.unethicalreading = true;
        if( args.length!=2 && args.length!=3) {
            System.err.println("Usage:");
            System.err.println("    java -jar scrivepdftools.jar httpserver -p [IP:]port");
            System.err.println("");
            System.err.println("    java -jar scrivepdftools.jar add-verification-pages config.json optional-input.pdf");
            System.err.println("    java -jar scrivepdftools.jar find-texts config.json optional-input.pdf");
            System.err.println("    java -jar scrivepdftools.jar extract-texts config.json optional-input.pdf");
            System.err.println("    java -jar scrivepdftools.jar normalize config.json optional-input.pdf");
            System.err.println("    java -jar scrivepdftools.jar select-and-clip config.json optional-input.pdf");
            System.err.println("");
            System.err.println("scrivepdftools uses the following products:");
            System.err.println("   iText by Bruno Lowagie, iText Group NV ");
            System.err.println("   snakeyaml");

        }
        else if (args[0].equals("httpserver")) {
            if (!args[1].equals("-p")) {
            	System.err.println("Usage:");
            	System.err.println("    java -jar scrivepdftools.jar httpserver -p [IP:]port");
            } else
            	WebServer.execute(args[0], args[2]);
        } else {
            execute(args[0], args[1], (args.length == 3) ? args[2] : null, null);
        }
    }
}
