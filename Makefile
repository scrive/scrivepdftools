#
#
# You may use 'make OPEN=open' so that generated PDFs open automatically
#
#

CLASSPATH=itextpdf-5.5.5.jar:itext-asian.jar:snakeyaml-1.12.jar:bcpkix-jdk15on-1.48.jar:bcprov-jdk15on-1.48.jar:metadata-extractor-2.6.4.jar:commons-fileupload-1.3.1.jar:commons-io-2.4.jar:xmpcore.jar:json-20090211.jar

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

server : scrivepdftools.jar
	mkdir -p test/results;                                                          \
	rm -f test/results/*.*;                                                         \
	if [ -f server.pid ]; then							\
	   kill $$(cat server.pid);							\
	   echo "HTTP server pid $$(cat server.pid) instakilled";			\
	   rm server.pid;								\
	   sleep 1;                                                                     \
	fi;										\
	java -Xmx1024M -jar scrivepdftools.jar httpserver -p 12344 & pid=$$!;		\
            { echo $$pid > server.pid;							\
	      echo "HTTP server pid $$pid started";					\
              sleep 60;									\
              if [ -f server.pid -a $$(cat server.pid) -eq $$pid ]; then                \
	          kill $$pid && rm server.pid && echo "HTTP server pid $$pid killed";	\
              fi;                                                                       \
	    } &										\
	sleep 1

.PHONY: server

clean :
	rm -f scrivepdftools.jar Manifest.txt
	rm -f classes/*.class
	rm -f test/results/*.*
	rm -f test/*.ext

classes/%.class : src/%.java
	if [ ! -d classes ]; then mkdir classes; fi
	javac -source 1.5 -target 1.5 -cp $(CLASSPATH) $< -sourcepath src -d classes

FONTS=assets/SourceSansPro-Light.ttf								\
      assets/NotoSans-Regular.ttf								\
      assets/NotoSansThai-Regular.ttf                                                           \
      assets/NotoSansHebrew-Regular.ttf


scrivepdftools.jar : classes/Main.class								\
                     classes/AddVerificationPages.class						\
                     classes/FindTexts.class							\
                     classes/ExtractTexts.class							\
                     classes/Normalize.class							\
                     classes/SelectAndClip.class						\
                     classes/RemoveJavaScript.class						\
                     classes/WebServer.class							\
                     classes/PageText.class							\
                     classes/Engine.class							\
                     classes/TextEngine.class							\
                     classes/PdfAdditionalInfo.class						\
                     classes/TextDump.class							\
                     classes/YamlSpec.class							\
                     classes/SealSpec.class							\
                     classes/MyRepresenter.class						\
                     assets/sealmarker.pdf							\
                     assets/test-client.html							\
	                 $(FONTS)
	echo "Main-Class: Main" > Manifest.txt
	echo "Class-Path: $(subst :, ,$(CLASSPATH))" >> Manifest.txt
	jar cfm $@ Manifest.txt assets/sealmarker.pdf assets/test-client.html $(FONTS) -C classes .

test : test-add-verification-pages								\
       test-find-texts										\
       test-extract-texts									\
       test-remove-javascript									\
       test-normalize										\
       test-remove-elements									\
       test-select-and-clip

test-add-verification-pages : 									\
       test/results/seal-simplest.pdf								\
       test/results/seal-simplest-verified.pdf							\
       test/results/seal-filetypes.pdf								\
       test/results/seal-filetypes-preseal.pdf							\
       test/results/seal-filetypes-us-letter.pdf						\
       test/results/seal-many-people.pdf							\
       test/results/seal-images.pdf								\
       test/results/seal-images-preseal.pdf							\
       test/results/seal-fields.pdf								\
       test/results/seal-fields-preseal.pdf							\
       test/results/example_spec.pdf								\
       test/results/missing-xmpcore.pdf

# On Mac OS X java has this misfeature of poping a dock icon that
# steals focus.  We disable that with `-Dapple.awt.UIElement=true`
# command line option.

test/results/seal-simplest.pdf : test/seal-simplest.json test/three-page-a4.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/add-verification-pages -o $@
	#java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/example_spec.pdf : test/example_spec.json test/three-page-a4.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/add-verification-pages -o $@
	#java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/field-positions.pdf : test/field_positions.json test/three-page-a4.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/add-verification-pages -o $@
	#java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-simplest-verified.pdf : test/seal-simplest-verified.json test/three-page-a4.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/add-verification-pages -o $@
	#java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-filetypes.pdf : test/seal-filetypes.json test/three-page-a4.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/add-verification-pages -o $@
	#java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-filetypes-preseal.pdf : test/seal-filetypes-preseal.json test/three-page-a4.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/add-verification-pages -o $@
	#java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-filetypes-us-letter.pdf : test/seal-filetypes-us-letter.json test/one-page-us-letter.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/add-verification-pages -o $@
	#java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-many-people.pdf : test/seal-many-people.json test/one-page-us-letter.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/add-verification-pages -o $@
	#java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-images.pdf : test/seal-images.json test/with-solid-background.pdf scrivepdftools.jar | server
	sed -e s!16bit-gray-alpha.png!`$(BASE64) test/16bit-gray-alpha.png`!g			\
	    -e s!grayscale-8bit.png!`$(BASE64) test/grayscale-8bit.png`!g			\
	    -e s!jpeg-image.jpg!`$(BASE64) test/jpeg-image.jpg`!g				\
	    -e s!jpeg-image-90.jpg!`$(BASE64) test/jpeg-image-90.jpg`!g				\
	    -e s!colormap-8bit-1.png!`$(BASE64) test/colormap-8bit-1.png`!g			\
	    -e s!colormap-8bit-2.png!`$(BASE64) test/colormap-8bit-2.png`!g			\
	    -e s!png-with-alpha.png!`$(BASE64) test/png-with-alpha.png`!g			\
	    -e s!8bit-rgba.png!`$(BASE64) test/8bit-rgba.png`!g					\
          $< > $<.ext
	curl -s -F config=@$<.ext                                  \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/add-verification-pages -o $@
	#java -jar scrivepdftools.jar add-verification-pages $<.ext
ifdef OPEN
	$(OPEN) $@
endif

test/results/missing-xmpcore.pdf : test/missing-xmpcore.json test/with-solid-background.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/add-verification-pages -o $@
	#java -Xmx512M -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/with-background.pdf : test/add-background.json test/good_avis.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/add-verification-pages -o $@
	#java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-images-preseal.pdf : test/seal-images.json test/with-solid-background.pdf scrivepdftools.jar | server
	sed -e s!16bit-gray-alpha.png!`$(BASE64) test/16bit-gray-alpha.png`!g			\
	    -e s!grayscale-8bit.png!`$(BASE64) test/grayscale-8bit.png`!g			\
	    -e s!jpeg-image.jpg!`$(BASE64) test/jpeg-image.jpg`!g				\
	    -e s!jpeg-image-90.jpg!`$(BASE64) test/jpeg-image-90.jpg`!g				\
	    -e 's!"preseal": false!"preseal": true!g'						\
	    -e s!colormap-8bit-1.png!`$(BASE64) test/colormap-8bit-1.png`!g			\
	    -e s!colormap-8bit-2.png!`$(BASE64) test/colormap-8bit-2.png`!g			\
	    -e s!png-with-alpha.png!`$(BASE64) test/png-with-alpha.png`!g			\
	    -e s!8bit-rgba.png!`$(BASE64) test/8bit-rgba.png`!g					\
	    -e 's!"test/results/seal-images.pdf"!"test/results/seal-images-preseal.pdf"!g'	\
          $< > $<.ext
	curl -s -F config=@$<.ext                                  \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/add-verification-pages -o $@
	#java -jar scrivepdftools.jar add-verification-pages $<.ext
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-fields.pdf : test/seal-fields.json test/three-page-a4.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/add-verification-pages -o $@
	#java -jar scrivepdftools.jar add-verification-pages $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/seal-fields-preseal.pdf : test/seal-fields.json test/three-page-a4.pdf scrivepdftools.jar | server
	sed -e 's!"preseal": false!"preseal": true!g'						\
        -e 's!"test/results/seal-fields.pdf"!"test/results/seal-fields-preseal.pdf"!g'		\
         $< > $<.ext
	curl -s -F config=@$<.ext                                  \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/add-verification-pages -o $@
	#java -jar scrivepdftools.jar add-verification-pages $<.ext
ifdef OPEN
	$(OPEN) $@
endif

test-find-texts : 										\
                  test/results/test-find-texts.find-output.yaml					\
                  test/results/test-find-texts-test-document.find-output.yaml			\
                  test/results/test-find-texts-out-of-order.find-output.yaml			\
                  test/results/test-find-texts-json-encoding.find-output.yaml			\
                  test/results/test-find-texts-crop-box.find-output.yaml			\
                  test/results/test-find-texts-arabic-contract.find-output.yaml			\
                  test/results/test-find-texts-all-pages.find-output.yaml

test/results/%.find-output.yaml :
	sed -e 's!"stampedOutput": ".*"!"stampedOutput": "'$(patsubst %.yaml,%.pdf,$@)'"!g'	\
          $< > $<.ext
	curl -s -F config=@$<.ext                                  \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/find-texts -o $(patsubst %.yaml,%-1.yaml,$@)
	#java -jar scrivepdftools.jar find-texts $<.ext $(word 2,$^) > $(patsubst %.yaml,%-1.yaml,$@)
ifdef OPEN
	$(OPEN) $(patsubst %.yaml,%.pdf,$@)
endif
ifdef ACCEPT
	if diff $(word 3,$^) $(patsubst %.yaml,%-1.yaml,$@); then				\
	    echo "";										\
	else											\
	    echo "Accepting new version of expect file: $(word 3,$^)";				\
	    cp $(patsubst %.yaml,%-1.yaml,$@) $(word 3,$^);					\
	fi
else
	diff $(word 3,$^) $(patsubst %.yaml,%-1.yaml,$@)
endif
	mv $(patsubst %.yaml,%-1.yaml,$@) $@

test/results/test-find-texts.find-output.yaml :							\
    test/find-texts.json									\
    test/three-page-a4.pdf									\
    test/test-find-texts.expect.yaml								\
    scrivepdftools.jar | server

test/results/test-find-texts-test-document.find-output.yaml :					\
    test/find-texts-test-document.json								\
    test/test-document.pdf									\
    test/test-find-texts-test-document.expect.yaml						\
    scrivepdftools.jar | server

test/results/test-find-texts-out-of-order.find-output.yaml :					\
    test/find-texts-out-of-order.json								\
    test/text-out-of-order.pdf									\
    test/test-find-texts-out-of-order.expect.yaml						\
    scrivepdftools.jar | server

test/results/test-find-texts-json-encoding.find-output.yaml :					\
    test/find-text-json-encoding.json								\
    test/three-page-a4.pdf									\
    test/test-find-texts-json-encoding.expect.yaml						\
    scrivepdftools.jar | server

test/results/test-find-texts-sales-contract.find-output.yaml :					\
    test/find-text-sales-contract.json								\
    test/sales_contract.pdf									\
    test/test-find-texts-sales-contract.expect.yaml						\
    scrivepdftools.jar | server

test/results/test-find-texts-crop-box.find-output.yaml:						\
    test/find-texts-crop-box.json								\
    test/glas-skadeanmalan-ryds-bilglas.pdf							\
    test/test-find-texts-crop-box.expect.yaml							\
    scrivepdftools.jar | server

test/results/test-find-texts-arabic-contract.find-output.yaml:					\
    test/find-texts-arabic-contract.json							\
    test/arabic_contract.pdf									\
    test/test-find-texts-arabic-contract.expect.yaml						\
    scrivepdftools.jar | server

test/results/test-find-texts-all-pages.find-output.yaml:					\
    test/find-texts-all-pages.json								\
    test/text-out-of-order.pdf									\
    test/test-find-texts-all-pages.expect.yaml							\
    scrivepdftools.jar | server

test-extract-texts : 										\
                     test/results/test-extract-texts.extract-output.yaml			\
                     test/results/test-extract-test-document.extract-output.yaml		\
                     test/results/test-extract-test-document-with-forms.extract-output.yaml	\
                     test/results/test-extract-texts-out-of-order.extract-output.yaml		\
                     test/results/test-extract-rotated.extract-output.yaml			\
                     test/results/test-extract-texts-sales-contract.extract-output.yaml		\
                     test/results/test-extract-cat-only.extract-output.yaml			\
                     test/results/test-extract-rotate-90.extract-output.yaml			\
                     test/results/test-extract-poor-mans-bold.extract-output.yaml		\
                     test/results/test-bB02_103_cerere.extract-output.yaml			\
                     test/results/test-extract-arabic-contract.extract-output.yaml		\
                     test/results/test-glas.extract-output.yaml					\
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
	sed -e 's!"stampedOutput": ".*"!"stampedOutput": "'$(patsubst %.yaml,%.pdf,$@)'"!g'	\
          $< > $<.ext
	curl -s -F config=@$<.ext                                  \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/extract-texts -o $(patsubst %.yaml,%-1.yaml,$@)
	#java -jar scrivepdftools.jar extract-texts $<.ext $(word 2,$^) > $(patsubst %.yaml,%-1.yaml,$@)
ifdef OPEN
	$(OPEN) $(patsubst %.yaml,%.pdf,$@)
endif
ifdef ACCEPT
	if diff $(word 3,$^) $(patsubst %.yaml,%-1.yaml,$@); then				\
	    echo "";										\
	else											\
	    echo "Accepting new version of expect file: $(word 3,$^)";				\
	    cp $(patsubst %.yaml,%-1.yaml,$@) $(word 3,$^);					\
	fi
else
	diff $(word 3,$^) $(patsubst %.yaml,%-1.yaml,$@)
endif
	mv $(patsubst %.yaml,%-1.yaml,$@) $@

test/results/test-extract-texts-sales-contract.extract-output.yaml :				\
    test/extract-texts-sales-contract.json							\
    test/sales_contract.pdf									\
    test/test-extract-texts-sales-contract.expect.yaml						\
    scrivepdftools.jar | server

test/results/test-extract-texts.extract-output.yaml :						\
    test/extract-texts.json									\
    test/three-page-a4.pdf									\
    test/test-extract-texts.expect.yaml								\
    scrivepdftools.jar | server

test/results/test-extract-texts-ligatures.extract-output.yaml :					\
    test/extract-texts.json									\
    test/ligatures.pdf										\
    test/test-extract-texts-ligatures.expect.yaml						\
    scrivepdftools.jar | server

test/results/test-extract-test-document.extract-output.yaml :					\
    test/extract-test-document.json								\
    test/test-document.pdf									\
    test/test-extract-test-document.expect.yaml							\
    scrivepdftools.jar | server

test/results/test-extract-test-document-with-forms.extract-output.yaml :			\
    test/extract-test-document.json								\
    test/document-with-text-in-forms.pdf							\
    test/test-extract-test-document-with-forms.expect.yaml					\
    scrivepdftools.jar | server

test/results/test-extract-texts-out-of-order.extract-output.yaml :				\
    test/extract-texts-whole-first-page.json							\
    test/text-out-of-order.pdf									\
    test/test-extract-texts-out-of-order.expect.yaml						\
    scrivepdftools.jar | server

test/results/test-extract-cat-only.extract-output.yaml :					\
    test/extract-texts-cat-only.json								\
    test/cat-only.pdf										\
    test/test-extract-cat-only.expect.yaml							\
    scrivepdftools.jar | server

test/results/test-extract-rotated.extract-output.yaml :						\
    test/extract-rotated.json									\
    test/rotated-text.pdf									\
    test/test-extract-rotated.expect.yaml							\
    scrivepdftools.jar | server

test/results/test-extract-rotate-90.extract-output.yaml :					\
    test/extract-texts-rotate-90.json								\
    test/fuck3.pdf										\
    test/test-extract-rotate-90.expect.yaml							\
    scrivepdftools.jar | server

test/results/test-extract-poor-mans-bold.extract-output.yaml :					\
    test/extract-texts-whole-first-page.json							\
    test/poor-mans-bold.pdf									\
    test/test-extract-poor-mans-bold.expect.yaml						\
    scrivepdftools.jar | server

test/results/test-bB02_103_cerere.extract-output.yaml :						\
    test/extract-texts-whole-first-page.json							\
    test/bB02_103_cerere.pdf									\
    test/test-extract-bB02_103_cerere.expect.yaml						\
    scrivepdftools.jar | server

test/results/test-extract-arabic-contract.extract-output.yaml:					\
    test/extract-texts-arabic-contract.json							\
    test/arabic_contract.pdf									\
    test/test-extract-texts-arabic-contract.expect.yaml						\
    scrivepdftools.jar | server

test/results/test-glas.extract-output.yaml :							\
    test/extract-texts-skadeanmalan.json							\
    test/glas-skadeanmalan-ryds-bilglas.pdf							\
    test/test-extract-texts-skadeanmalan.expect.yaml						\
    scrivepdftools.jar | server

test-normalize : scrivepdftools.jar								\
    test/results/document-with-text-in-forms-flattened.pdf					\
    test/results/unrotated-text.pdf								\
    test/results/unrotated3.pdf

test-remove-javascript : scrivepdftools.jar							\
    test/results/document-with-removed-javascript.pdf


test/results/document-with-text-in-forms-flattened.pdf : test/normalize.json test/document-with-text-in-forms.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/normalize -o $@
	#java -jar scrivepdftools.jar normalize $<
ifdef OPEN
	$(OPEN) $@
endif


test/results/unrotated-text.pdf : test/normalize-rotated.json test/rotated-text.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/normalize -o $@
	#java -jar scrivepdftools.jar normalize $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/unrotated3.pdf : test/normalize-rotated3.json test/fuck3.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/normalize -o $@
	#java -jar scrivepdftools.jar normalize $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/document-with-removed-javascript.pdf : test/remove-javascript.json test/document-with-javascript.pdf scrivepdftools.jar
	java -jar scrivepdftools.jar remove-javascript $<
ifdef OPEN
	$(OPEN) $@
endif

test-remove-elements : test/results/unsealed.pdf

test/results/unsealed.pdf : test/remove-all-elements.json test/sealed.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/remove-scrive-elements -o $@
	#java -jar scrivepdftools.jar remove-scrive-elements $<
ifdef OPEN
	$(OPEN) $@
endif

test-select-and-clip : test/results/sealed-document-sealing-removed.pdf				\
                       test/results/signed-demo-contract-sealing-removed.pdf

test/results/sealed-document-sealing-removed.pdf : test/select-and-clip.json test/document-sealed.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/select-and-clip -o $@
	#java -jar scrivepdftools.jar select-and-clip $<
ifdef OPEN
	$(OPEN) $@
endif

test/results/signed-demo-contract-sealing-removed.pdf : test/select-and-clip-signed-demo-contract.json test/signed-demo-contract.pdf scrivepdftools.jar | server
	curl -s -F config=@$<                                      \
                -F pdf=@$(word 2,$^)                               \
                http://127.0.0.1:12344/select-and-clip -o $@
	#java -jar scrivepdftools.jar select-and-clip $<
ifdef OPEN
	$(OPEN) $@
endif
