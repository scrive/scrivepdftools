
CLASSPATH1=itext-asian.jar:snakeyaml-1.12.jar:bcpkix-jdk15on-1.48.jar:bcprov-jdk15on-1.48.jar
CLASSPATH=.:itextpdf-5.4.5.jar:$(CLASSPATH1)

ifeq ($(OS),Windows_NT)
else
    UNAME_S := $(shell uname -s)
    ifeq ($(UNAME_S),Linux)
        BASE64=base64 -w 0 -i
    endif
    ifeq ($(UNAME_S),Darwin)
        BASE64=base64 -b 0 -i
    endif
endif

jar : scrivepdftools.jar

classes/%.class : src/%.java
	if [ ! -d classes ]; then mkdir classes; fi
	javac -source 1.5 -target 1.5 -cp $(CLASSPATH) $< -sourcepath src -d classes

scrivepdftools.jar : Manifest.txt classes/Main.class classes/AddVerificationPages.class classes/FindTexts.class classes/ExtractTexts.class assets/sealmarker.pdf assets/SourceSansPro-Light.ttf
	jar cfm $@ Manifest.txt assets/sealmarker.pdf assets/SourceSansPro-Light.ttf -C classes .

test : test-add-verification-pages test-find-texts test-extract-texts

test-add-verification-pages : test/seal-simplest.pdf \
       test/seal-simplest-verified.pdf \
       test/seal-many-people.pdf \
       test/seal-images.pdf \
       test/seal-images-preseal.pdf \
       test/seal-fields.pdf \
       test/seal-fields-preseal.pdf


test/seal-simplest.pdf : test/seal-simplest.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
	open $@

test/seal-simplest-verified.pdf : test/seal-simplest-verified.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
	open $@

test/seal-many-people.pdf : test/seal-many-people.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
	open $@

test/seal-images.pdf : test/seal-images.json scrivepdftools.jar
	sed -e s!16bit-gray-alpha.png!`$(BASE64) test/16bit-gray-alpha.png`!g \
        -e s!grayscale-8bit.png!`$(BASE64) test/grayscale-8bit.png`!g \
        -e s!jpeg-image.jpg!`$(BASE64) test/jpeg-image.jpg`!g \
          $< > $<.ext
	java -jar scrivepdftools.jar add-verification-pages $<.ext
	open $@

test/seal-images-preseal.pdf : test/seal-images.json scrivepdftools.jar
	sed -e s!16bit-gray-alpha.png!`$(BASE64) test/16bit-gray-alpha.png`!g \
        -e s!grayscale-8bit.png!`$(BASE64) test/grayscale-8bit.png`!g \
        -e s!jpeg-image.jpg!`$(BASE64) test/jpeg-image.jpg`!g \
        -e 's!"preseal": false!"preseal": true!g' \
        -e 's!"test/seal-images.pdf"!"test/seal-images-preseal.pdf"!g' \
          $< > $<.ext
	java -jar scrivepdftools.jar add-verification-pages $<.ext
	open $@

test/seal-fields.pdf : test/seal-fields.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
	open $@

test/seal-fields-preseal.pdf : test/seal-fields.json scrivepdftools.jar
	sed -e 's!"preseal": false!"preseal": true!g' \
        -e 's!"test/seal-fields.pdf"!"test/seal-fields-preseal.pdf"!g' \
         $< > $<.ext
	java -jar scrivepdftools.jar add-verification-pages $<.ext
	open $@

test-find-texts : test/test-find-texts.output.yaml

test/test-find-texts.output.yaml : test/find-texts.json scrivepdftools.jar
	java -jar scrivepdftools.jar find-texts $< > $@
	diff test/test-find-texts.expect.yaml $@

test-extract-texts : test/test-extract-texts.output.yaml test/test-extract-test-document.output.yaml

test/test-extract-texts.output.yaml : test/extract-texts.json scrivepdftools.jar
	java -jar scrivepdftools.jar extract-texts $< > $@
	diff test/test-extract-texts.expect.yaml $@
	open test/three-page-a4-stamped.pdf

test/test-extract-test-document.output.yaml : test/extract-test-document.json scrivepdftools.jar
	java -jar scrivepdftools.jar extract-texts $< > $@
	diff test/test-extract-test-document.expect.yaml $@
	open test/test-document-stamped.pdf
