#
#
# You may use 'make OPEN=open' so that generated PDFs open automatically
#
#

CLASSPATH=itextpdf-5.5.5.jar:itext-asian.jar:snakeyaml-1.12.jar:bcpkix-jdk15on-1.48.jar:bcprov-jdk15on-1.48.jar:metadata-extractor-2.6.4.jar:commons-fileupload-1.3.1.jar:commons-io-2.4.jar:xmpcore.jar

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

clean :
	rm -f scrivepdftools.jar Manifest.txt	
	rm -f classes/*.class
	rm -f test/results/*.*
	rm -f test/*.ext

classes/%.class : src/%.java
	if [ ! -d classes ]; then mkdir classes; fi
	javac -source 1.5 -target 1.5 -cp $(CLASSPATH) $< -sourcepath src -d classes

FONTS=assets/SourceSansPro-Light.ttf \
      assets/NotoSans-Regular.ttf    \
      assets/NotoSansThai-Regular.ttf


scrivepdftools.jar : classes/Main.class \
                     classes/AddVerificationPages.class \
                     classes/FindTexts.class \
                     classes/ExtractTexts.class \
                     classes/Normalize.class \
                     classes/SelectAndClip.class \
                     classes/WebServer.class \
                     classes/PageText.class \
                     classes/Engine.class \
                     classes/TextEngine.class \
                     classes/PdfAdditionalInfo.class \
                     classes/TextDump.class \
                     classes/YamlSpec.class \
                     classes/MyRepresenter.class \
                     assets/sealmarker.pdf \
                     assets/test-client.html \
	                 $(FONTS)
	echo "Main-Class: Main" > Manifest.txt
	echo "Class-Path: $(subst :, ,$(CLASSPATH))" >> Manifest.txt
	jar cfm $@ Manifest.txt assets/sealmarker.pdf assets/test-client.html $(FONTS) -C classes .

test : test-add-verification-pages \
       test-find-texts \
       test-extract-texts \
       test-normalize \
       test-select-and-clip

test-add-verification-pages : scrivepdftools.jar \
       test/results/seal-simplest.pdf \
       test/results/seal-simplest-verified.pdf \
       test/results/seal-filetypes.pdf \
       test/results/seal-filetypes-preseal.pdf \
       test/results/seal-filetypes-us-letter.pdf \
       test/results/seal-many-people.pdf \
       test/results/seal-images.pdf \
       test/results/seal-images-preseal.pdf \
       test/results/seal-fields.pdf \
       test/results/seal-fields-preseal.pdf \
       test/results/example_spec.pdf \
       test/results/missing-xmpcore.pdf


test/results/seal-simplest.pdf : test/seal-simplest.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/example_spec.pdf : test/example_spec.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/field-positions.pdf : test/field_positions.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-simplest-verified.pdf : test/seal-simplest-verified.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-filetypes.pdf : test/seal-filetypes.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-filetypes-preseal.pdf : test/seal-filetypes-preseal.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-filetypes-us-letter.pdf : test/seal-filetypes-us-letter.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-many-people.pdf : test/seal-many-people.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-images.pdf : test/seal-images.json scrivepdftools.jar
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

test/results/missing-xmpcore.pdf : test/missing-xmpcore.json scrivepdftools.jar
	java -Xmx512M -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/with-background.pdf : test/add-background.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-images-preseal.pdf : test/seal-images.json scrivepdftools.jar
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

test/results/seal-fields.pdf : test/seal-fields.json scrivepdftools.jar
	java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-fields-preseal.pdf : test/seal-fields.json scrivepdftools.jar
	sed -e 's!"preseal": false!"preseal": true!g' \
        -e 's!"test/seal-fields.pdf"!"test/seal-fields-preseal.pdf"!g' \
         $< > $<.ext
	java -jar scrivepdftools.jar add-verification-pages $<.ext
ifdef OPEN
	$(OPEN) $@
endif

test-find-texts : scrivepdftools.jar \
                  test/results/test-find-texts.find-output.yaml \
                  test/results/test-find-texts-test-document.find-output.yaml \
                  test/results/test-find-texts-out-of-order.find-output.yaml \
                  test/results/test-find-texts-json-encoding.find-output.yaml \
                  test/results/test-find-texts-crop-box.find-output.yaml \
                  test/results/test-find-texts-arabic-contract.find-output.yaml \
                  test/results/test-find-texts-all-pages.find-output.yaml

test/results/%.find-output.yaml :
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

test/results/test-find-texts.find-output.yaml :				 \
    test/find-texts.json							 \
    test/three-page-a4.pdf							 \
    test/test-find-texts.expect.yaml				 \
    scrivepdftools.jar

test/results/test-find-texts-test-document.find-output.yaml :\
    test/find-texts-test-document.json				 \
    test/test-document.pdf							 \
    test/test-find-texts-test-document.expect.yaml	 \
    scrivepdftools.jar

test/results/test-find-texts-out-of-order.find-output.yaml : \
    test/find-texts-out-of-order.json				 \
    test/text-out-of-order.pdf						 \
    test/test-find-texts-out-of-order.expect.yaml	 \
    scrivepdftools.jar

test/results/test-find-texts-json-encoding.find-output.yaml :\
    test/find-text-json-encoding.json				 \
    test/three-page-a4.pdf							 \
    test/test-find-texts-json-encoding.expect.yaml	 \
    scrivepdftools.jar

test/results/test-find-texts-sales-contract.find-output.yaml :\
    test/find-text-sales-contract.json	   			 \
    test/sales_contract.pdf							 \
    test/test-find-texts-sales-contract.expect.yaml	 \
    scrivepdftools.jar

test/results/test-find-texts-crop-box.find-output.yaml:\
    test/find-texts-crop-box.json               \
    test/glas-skadeanmalan-ryds-bilglas.pdf    \
    test/test-find-texts-crop-box.expect.yaml  \
    scrivepdftools.jar

test/results/test-find-texts-arabic-contract.find-output.yaml: \
    test/find-texts-arabic-contract.json                       \
    test/arabic_contract.pdf    \
    test/test-find-texts-arabic-contract.expect.yaml  \
    scrivepdftools.jar

test/results/test-find-texts-all-pages.find-output.yaml:\
    test/find-texts-all-pages.json             \
    test/text-out-of-order.pdf                 \
    test/test-find-texts-all-pages.expect.yaml \
    scrivepdftools.jar

test-extract-texts : scrivepdftools.jar \
                     test/results/test-extract-texts.extract-output.yaml                   \
                     test/results/test-extract-test-document.extract-output.yaml           \
                     test/results/test-extract-test-document-with-forms.extract-output.yaml\
                     test/results/test-extract-texts-out-of-order.extract-output.yaml      \
                     test/results/test-extract-rotated.extract-output.yaml                 \
                     test/results/test-extract-texts-sales-contract.extract-output.yaml	   \
                     test/results/test-extract-cat-only.extract-output.yaml                \
                     test/results/test-extract-rotate-90.extract-output.yaml               \
                     test/results/test-extract-poor-mans-bold.extract-output.yaml          \
                     test/results/test-bB02_103_cerere.extract-output.yaml                 \
                     test/results/test-extract-arabic-contract.extract-output.yaml         \
                     test/results/test-glas.extract-output.yaml                            \
                     test/results/test-extract-texts-ligatures.extract-output.yaml

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
test/results/%.extract-output.yaml :
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

test/results/test-extract-texts-sales-contract.extract-output.yaml :	\
    test/extract-texts-sales-contract.json						\
    test/sales_contract.pdf										\
    test/test-extract-texts-sales-contract.expect.yaml			\
    scrivepdftools.jar

test/results/test-extract-texts.extract-output.yaml :					\
    test/extract-texts.json										\
    test/three-page-a4.pdf										\
    test/test-extract-texts.expect.yaml							\
    scrivepdftools.jar

test/results/test-extract-texts-ligatures.extract-output.yaml :					\
    test/extract-texts.json										\
    test/ligatures.pdf										\
    test/test-extract-texts-ligatures.expect.yaml							\
    scrivepdftools.jar

test/results/test-extract-test-document.extract-output.yaml :			\
    test/extract-test-document.json								\
    test/test-document.pdf										\
    test/test-extract-test-document.expect.yaml					\
    scrivepdftools.jar

test/results/test-extract-test-document-with-forms.extract-output.yaml :\
    test/extract-test-document.json								\
    test/document-with-text-in-forms.pdf						\
    test/test-extract-test-document-with-forms.expect.yaml		\
    scrivepdftools.jar

test/results/test-extract-texts-out-of-order.extract-output.yaml :		\
    test/extract-texts-whole-first-page.json					\
    test/text-out-of-order.pdf									\
    test/test-extract-texts-out-of-order.expect.yaml			\
    scrivepdftools.jar

test/results/test-extract-cat-only.extract-output.yaml :				\
    test/extract-texts-cat-only.json							\
    test/cat-only.pdf											\
    test/test-extract-cat-only.expect.yaml						\
    scrivepdftools.jar

test/results/test-extract-rotated.extract-output.yaml :\
    test/extract-rotated.json				   \
    test/rotated-text.pdf					   \
    test/test-extract-rotated.expect.yaml	   \
    scrivepdftools.jar

test/results/test-extract-rotate-90.extract-output.yaml :               \
    test/extract-texts-rotate-90.json                           \
    test/fuck3.pdf                                              \
    test/test-extract-rotate-90.expect.yaml                     \
    scrivepdftools.jar

test/results/test-extract-poor-mans-bold.extract-output.yaml :          \
    test/extract-texts-whole-first-page.json                    \
    test/poor-mans-bold.pdf                                     \
    test/test-extract-poor-mans-bold.expect.yaml                \
    scrivepdftools.jar

test/results/test-bB02_103_cerere.extract-output.yaml :         \
    test/extract-texts-whole-first-page.json                    \
    test/bB02_103_cerere.pdf                                    \
    test/test-extract-bB02_103_cerere.expect.yaml               \
    scrivepdftools.jar

test/results/test-extract-arabic-contract.extract-output.yaml:  \
    test/extract-texts-arabic-contract.json                     \
    test/arabic_contract.pdf                                    \
    test/test-extract-texts-arabic-contract.expect.yaml         \
    scrivepdftools.jar

test/results/test-glas.extract-output.yaml :         \
    test/extract-texts-skadeanmalan.json                    \
    test/glas-skadeanmalan-ryds-bilglas.pdf                                    \
    test/test-extract-texts-skadeanmalan.expect.yaml               \
    scrivepdftools.jar

test-normalize : scrivepdftools.jar \
    test/results/document-with-text-in-forms-flattened.pdf \
    test/results/unrotated-text.pdf                        \
    test/results/unrotated3.pdf
                 

test/results/document-with-text-in-forms-flattened.pdf : test/normalize.json test/document-with-text-in-forms.pdf scrivepdftools.jar
	java -jar scrivepdftools.jar normalize $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/unrotated-text.pdf : test/normalize-rotated.json test/rotated-text.pdf scrivepdftools.jar
	java -jar scrivepdftools.jar normalize $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/unrotated3.pdf : test/normalize-rotated3.json test/fuck3.pdf scrivepdftools.jar
	java -jar scrivepdftools.jar normalize $<
ifdef OPEN
	$(OPEN) $@
endif

test-select-and-clip : test/results/sealed-document-sealing-removed.pdf \
                       test/results/signed-demo-contract-sealing-removed.pdf

test/results/sealed-document-sealing-removed.pdf : test/select-and-clip.json test/document-sealed.pdf scrivepdftools.jar
	java -jar scrivepdftools.jar select-and-clip $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/signed-demo-contract-sealing-removed.pdf : test/select-and-clip-signed-demo-contract.json test/signed-demo-contract.pdf scrivepdftools.jar
	java -jar scrivepdftools.jar select-and-clip $<
ifdef OPEN
	$(OPEN) $@
endif
