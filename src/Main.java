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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketException;

import com.itextpdf.text.DocumentException;

public class Main
{
    public static Engine getEngine(String command) {
        if( command.equals("add-verification-pages"))
            return new AddVerificationPages();
        else if( command.equals("find-texts"))
            return new FindTexts();
        else if( command.equals("extract-texts"))
            return new ExtractTexts();
        else if( command.equals("normalize"))
            return new Normalize();
        else if( command.equals("select-and-clip"))
            return new SelectAndClip();
        else if( command.equals("remove-scrive-elements"))
            return new RemoveScriveElements();
        System.err.println("Error: Uknown command: " + command);
        return null;
    }

    public byte[] execute(String command, byte[] spec, byte[] pdf)
        throws IOException, DocumentException
    {
        Engine engine = getEngine(command);
        if (null == engine)
            return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        engine.Init(new ByteArrayInputStream(spec), null, null);
        engine.execute(new ByteArrayInputStream(pdf), out);
        return out.toByteArray();
    }

    public static void main(String[] args)
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
            System.err.println("    java -jar scrivepdftools.jar remove-scrive-elements config.json optional-input.pdf");
            System.err.println("");
            System.err.println("scrivepdftools uses the following products:");
            System.err.println("   iText by Bruno Lowagie, iText Group NV ");
            System.err.println("   snakeyaml");

        }
        else if (args[0].equals("httpserver")) {
            if (!args[1].equals("-p")) {
                System.err.println("Usage:");
                System.err.println("    java -jar scrivepdftools.jar httpserver -p [IP:]port");
            } else {
                final int i = args[2].lastIndexOf(":", args[2].length());
                final String ip = ( i < 0 ) ? null : args[2].substring(0, i);
                final String port = args[2].substring(i + 1);
                try {
                    WebServer.start(args[0], ip, Integer.valueOf(port));
                } catch (NumberFormatException e) {
                    System.err.println("Error: Invalid port number: " + port);
                    System.err.println("Usage:");
                    System.err.println("    java -jar scrivepdftools.jar httpserver -p [IP:]port");
                } catch (SocketException e) {
                    System.err.println("Error: Invalid IP address: " + ip);
                    e.printStackTrace(System.err);                    
                } catch (IOException e) {
                    e.printStackTrace(System.err);                    
                }
            }
        } else {
            try {
                Engine engine = getEngine(args[0]);
                if (null != engine)
                    engine.execute(args[1], (args.length == 3) ? args[2] : null, null);
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }
}
