
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
        }
        else {
            if( args[0].equals("add-verification-pages")) {
                AddVerificationPages.execute(args[1]);
            }
            else if( args[0].equals("find-texts")) {
                FindTexts.execute(args[1]);
            }
            else {
                System.err.println("Uknown verb " + args[0]);
            }
        }
    }
}
