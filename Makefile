#
#
# You may use 'make OPEN=open' so that generated PDFs open automatically
#
#

CLASSPATH=itextpdf-5.5.3.jar:itext-asian.jar:snakeyaml-1.12.jar:bcpkix-jdk15on-1.48.jar:bcprov-jdk15on-1.48.jar:metadata-extractor-2.6.4.jar

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

FONTS=assets/SourceSansPro-Light.ttf \
      assets/NotoSans-Regular.ttf

Manifest.txt :
	echo "Main-Class: Main" > $@
	echo "Class-Path: $(subst :, ,$(CLASSPATH))" >> $@


scrivepdftools.jar : Manifest.txt \
                     classes/Main.class \
                     classes/AddVerificationPages.class \
                     classes/FindTexts.class \
                     classes/ExtractTexts.class \
                     classes/Normalize.class \
                     classes/SelectAndClip.class \
                     assets/sealmarker.pdf \
	                 $(FONTS)
	jar cfm $@ Manifest.txt assets/sealmarker.pdf $(FONTS) -C classes .

test : test-add-verification-pages \
       test-find-texts \
       test-extract-texts \
       test-normalize \
       test-select-and-clip

test-add-verification-pages :\
       test/seal-simplest.pdf \
       test/seal-simplest-verified.pdf \
       test/seal-filetypes.pdf \
       test/seal-filetypes-preseal.pdf \
       test/seal-filetypes-us-letter.pdf \
       test/seal-many-people.pdf \
       test/seal-images.pdf \
       test/seal-images-preseal.pdf \
       test/seal-fields.pdf \
       test/seal-fields-preseal.pdf \
       test/example_spec.pdf


test/seal-simplest.pdf : test/seal-simplest.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/example_spec.pdf : test/example_spec.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/field-positions.pdf : test/field_positions.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/seal-simplest-verified.pdf : test/seal-simplest-verified.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/seal-filetypes.pdf : test/seal-filetypes.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/seal-filetypes-preseal.pdf : test/seal-filetypes-preseal.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/seal-filetypes-us-letter.pdf : test/seal-filetypes-us-letter.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/seal-many-people.pdf : test/seal-many-people.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/seal-images.pdf : test/seal-images.json scrivepdftools.jar
	sed -e s!16bit-gray-alpha.png!`$(BASE64) test/16bit-gray-alpha.png`!g \
        -e s!grayscale-8bit.png!`$(BASE64) test/grayscale-8bit.png`!g \
        -e s!jpeg-image.jpg!`$(BASE64) test/jpeg-image.jpg`!g \
        -e s!jpeg-image-90.jpg!`$(BASE64) test/jpeg-image-90.jpg`!g \
        -e s!colormap-8bit-1.png!`$(BASE64) test/colormap-8bit-1.png`!g \
        -e s!colormap-8bit-2.png!`$(BASE64) test/colormap-8bit-2.png`!g \
        -e s!png-with-alpha.png!`$(BASE64) test/png-with-alpha.png`!g \
        -e s!8bit-rgba.png!`$(BASE64) test/8bit-rgba.png`!g \
          $< > $<.ext
	java -jar scrivepdftools.jar add-verification-pages $<.ext
ifdef OPEN
	$(OPEN) $@
endif

test/seal-images-preseal.pdf : test/seal-images.json scrivepdftools.jar
	sed -e s!16bit-gray-alpha.png!`$(BASE64) test/16bit-gray-alpha.png`!g \
        -e s!grayscale-8bit.png!`$(BASE64) test/grayscale-8bit.png`!g \
        -e s!jpeg-image.jpg!`$(BASE64) test/jpeg-image.jpg`!g \
        -e s!jpeg-image-90.jpg!`$(BASE64) test/jpeg-image-90.jpg`!g \
        -e 's!"preseal": false!"preseal": true!g' \
        -e s!colormap-8bit-1.png!`$(BASE64) test/colormap-8bit-1.png`!g \
        -e s!colormap-8bit-2.png!`$(BASE64) test/colormap-8bit-2.png`!g \
        -e s!png-with-alpha.png!`$(BASE64) test/png-with-alpha.png`!g \
        -e s!8bit-rgba.png!`$(BASE64) test/8bit-rgba.png`!g \
        -e 's!"test/seal-images.pdf"!"test/seal-images-preseal.pdf"!g' \
          $< > $<.ext
	java -jar scrivepdftools.jar add-verification-pages $<.ext
ifdef OPEN
	$(OPEN) $@
endif

test/seal-fields.pdf : test/seal-fields.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/seal-fields-preseal.pdf : test/seal-fields.json scrivepdftools.jar
	sed -e 's!"preseal": false!"preseal": true!g' \
        -e 's!"test/seal-fields.pdf"!"test/seal-fields-preseal.pdf"!g' \
         $< > $<.ext
	java -jar scrivepdftools.jar add-verification-pages $<.ext
ifdef OPEN
	$(OPEN) $@
endif

test-find-texts : test/test-find-texts.find-output.yaml \
                  test/test-find-texts-test-document.find-output.yaml \
                  test/test-find-texts-out-of-order.find-output.yaml \
                  test/test-find-texts-json-encoding.find-output.yaml

test/%.find-output.yaml :
	sed -e 's!"stampedOutput": ".*"!"stampedOutput": "'$(patsubst %.yaml,%.pdf,$@)'"!g' \
          $< > $<.ext
	java -jar scrivepdftools.jar find-texts $<.ext $(word 2,$^) > $(patsubst %.yaml,%-1.yaml,$@)
ifdef OPEN
	$(OPEN) $(patsubst %.yaml,%.pdf,$@)
endif
ifdef ACCEPT
	if diff $(word 3,$^) $(patsubst %.yaml,%-1.yaml,$@); then     \
	    echo "";												  \
	else														  \
	    echo "Accepting new version of expect file: $(word 3,$^)";\
	    cp $(patsubst %.yaml,%-1.yaml,$@) $(word 3,$^);			  \
	fi
else
	diff $(word 3,$^) $(patsubst %.yaml,%-1.yaml,$@)
endif
	mv $(patsubst %.yaml,%-1.yaml,$@) $@

test/test-find-texts.find-output.yaml : test/find-texts.json test/three-page-a4.pdf test/test-find-texts.expect.yaml scrivepdftools.jar

test/test-find-texts-test-document.find-output.yaml : test/find-texts-test-document.json test/test-document.pdf test/test-find-texts-test-document.expect.yaml scrivepdftools.jar

test/test-find-texts-out-of-order.find-output.yaml : test/find-texts-out-of-order.json test/text-out-of-order.pdf test/test-find-texts-out-of-order.expect.yaml scrivepdftools.jar

test/test-find-texts-json-encoding.find-output.yaml : test/find-text-json-encoding.json test/three-page-a4.pdf test/test-find-texts-json-encoding.expect.yaml scrivepdftools.jar

test-extract-texts : test/test-extract-texts.extract-output.yaml \
                     test/test-extract-test-document.extract-output.yaml \
	                 test/test-extract-test-document-with-forms.extract-output.yaml \
                     test/test-extract-texts-out-of-order.extract-output.yaml

#
# Note about organization of tests here.
#
# A test is a tripple (json spec, pdf input, expected output). Those
# need to be specified in this order as 1st, 2nd and 3rd
# dependency. For example:
#
# test1.extract-output.yaml : spec.json input-document.pdf expected-output.yaml relevant-java.jar
#
# This setup allows to check a single spec against many inputs,
# effectivelly enabling easy creation of MxN matrix.
#
# Which process to use is encoded in output 'extended extension'. For
# text extraction output has to end with '.extract-output.yaml'.
#
# The rule below knows how to use each dependency.  It is ok to have
# more dependencies, but those will be ignored.
test/%.extract-output.yaml :
	sed -e 's!"stampedOutput": ".*"!"stampedOutput": "'$(patsubst %.yaml,%.pdf,$@)'"!g' \
          $< > $<.ext
	java -jar scrivepdftools.jar extract-texts $<.ext $(word 2,$^) > $(patsubst %.yaml,%-1.yaml,$@)
ifdef OPEN
	$(OPEN) $(patsubst %.yaml,%.pdf,$@)
endif
ifdef ACCEPT
	if diff $(word 3,$^) $(patsubst %.yaml,%-1.yaml,$@); then     \
	    echo "";												  \
	else														  \
	    echo "Accepting new version of expect file: $(word 3,$^)";\
	    cp $(patsubst %.yaml,%-1.yaml,$@) $(word 3,$^);			  \
	fi
else
	diff $(word 3,$^) $(patsubst %.yaml,%-1.yaml,$@)
endif
	mv $(patsubst %.yaml,%-1.yaml,$@) $@

test/test-extract-texts.extract-output.yaml : test/extract-texts.json test/three-page-a4.pdf test/test-extract-texts.expect.yaml scrivepdftools.jar

test/test-extract-test-document.extract-output.yaml : test/extract-test-document.json test/test-document.pdf test/test-extract-test-document.expect.yaml scrivepdftools.jar

test/test-extract-test-document-with-forms.extract-output.yaml : test/extract-test-document.json test/document-with-text-in-forms.pdf test/test-extract-test-document-with-forms.expect.yaml scrivepdftools.jar

test/test-extract-texts-out-of-order.extract-output.yaml : test/extract-texts-whole-first-page.json test/text-out-of-order.pdf test/test-extract-texts-out-of-order.expect.yaml scrivepdftools.jar

test-normalize : test/document-with-text-in-forms-flattened.pdf

test/document-with-text-in-forms-flattened.pdf : test/normalize.json test/document-with-text-in-forms.pdf scrivepdftools.jar
	java -jar scrivepdftools.jar normalize $<
ifdef OPEN
	$(OPEN) $@
endif

test-select-and-clip : test/sealed-document-sealing-removed.pdf \
                       test/signed-demo-contract-sealing-removed.pdf

test/sealed-document-sealing-removed.pdf : test/select-and-clip.json test/document-sealed.pdf scrivepdftools.jar
	java -jar scrivepdftools.jar select-and-clip $<
ifdef OPEN
	$(OPEN) $@
endif

test/signed-demo-contract-sealing-removed.pdf : test/select-and-clip-signed-demo-contract.json test/signed-demo-contract.pdf scrivepdftools.jar
	java -jar scrivepdftools.jar select-and-clip $<
ifdef OPEN
	$(OPEN) $@
endif
