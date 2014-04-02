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
/*
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.net.URL;
import java.lang.Character.UnicodeBlock;
*/

import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.nodes.*;
import org.yaml.snakeyaml.introspector.Property;


/*
 * MyRepresenter exists for the sole purpose of putting strings in
 * double quotes.
 *
 * Using DumperOptions.ScalarStyle.DOUBLE_QUOTED had this additional
 * feature of putting also numbers (ints and floats) in quotes and
 * tagging them with tags. To get around this I have to write this
 * function.
 *
 * This has also the nice property of putting key names in double
 * quotes producing well formed json.
 */
class MyRepresenter extends Representer
{
    protected Node representScalar(Tag tag,
                                   String value,
                                   Character c) {
        if( tag==Tag.STR ) {
            return super.representScalar(tag,value,DumperOptions.ScalarStyle.DOUBLE_QUOTED.getChar());
        }
        else {
            return super.representScalar(tag,value,c);
        }
    }
    @Override
    protected NodeTuple representJavaBeanProperty(Object javaBean, Property property,
                                                  Object propertyValue, Tag customTag) {
        if (propertyValue == null) {
            return null;
        } else {
            return super
                .representJavaBeanProperty(javaBean, property, propertyValue, customTag);
        }
    }
};
