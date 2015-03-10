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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class YamlSpec {
    public String input = null;
    public String output = null;
    public String dumpPath = null;

    static HashMap<Class<?>, ArrayList<TypeDescription>> td = new HashMap<Class<?>, ArrayList<TypeDescription>>();

    static void setTypeDescriptors(Class<?> c, ArrayList<TypeDescription> t) {
        td.put(c, t);
    }
    
    // TODO: calling this is too complicated... and one can forget about providing correct type descritors !
    public static <T extends YamlSpec> T loadFromStream(InputStream input, Class<T> specClass) throws IOException {
        Constructor constructor = new Constructor(specClass);
        constructor.setPropertyUtils(constructor.getPropertyUtils()); // seems awkward but is necessary for setSkipMissingProperties() to work
        constructor.getPropertyUtils().setSkipMissingProperties(true);

        /*
         * Java reflection is missing some crucial information about
         * elements of containers.  Add this information here.
         */
        ArrayList<TypeDescription> t = td.get(specClass);
        if (t == null)
            constructor.addTypeDescription(new TypeDescription(specClass));
        else
            for (TypeDescription i : t)
                constructor.addTypeDescription(i);
                
        
        Yaml yaml = new Yaml(constructor);
        T spec = (T)yaml.load(input);
        /*
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        System.out.println(yaml.dump(spec));
        */
        return spec;
    }
}
