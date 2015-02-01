package org.asciidoctor.maven.test

import org.apache.maven.plugin.MojoExecutionException
import org.asciidoctor.maven.AsciidoctorMojo
import org.asciidoctor.maven.processors.ProcessorConfiguration
import org.asciidoctor.maven.test.processors.ChangeAttributeValuePreprocessor
import org.asciidoctor.maven.test.processors.FailingPreprocessor
import org.asciidoctor.maven.test.processors.GistBlockMacroProcessor
import org.asciidoctor.maven.test.processors.ManpageInlineMacroProcessor
import org.asciidoctor.maven.test.processors.MetaDocinfoProcessor
import org.asciidoctor.maven.test.processors.UriIncludeProcessor
import org.asciidoctor.maven.test.processors.YellBlockProcessor
import org.jruby.exceptions.RaiseException

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Specific tests to validate usage of AsciidoctorJ extension in AsciidoctorMojo.
 * 
 * Most of the examples have been directly adapted from the ones found in AsciidoctorJ 
 * documentation (https://github.com/asciidoctor/asciidoctorj/blob/master/README.adoc)
 *
 * @author abelsromero
 */
class AsciidoctorMojoProcessorsTest extends Specification {

    static final String SRC_DIR = 'target/test-classes/src/asciidoctor/'
    static final String OUTPUT_DIR = 'target/asciidoctor-output-extensions'

    def "fails because processor is not found in classpath"() {
        setup:
            File srcDir = new File(SRC_DIR)
            File outputDir = new File("${OUTPUT_DIR}/preprocessor/${System.currentTimeMillis()}")
    
            if (!outputDir.exists())
                outputDir.mkdir()
        when:
            AsciidoctorMojo mojo = new AsciidoctorMojo()
            mojo.backend = 'html'
            mojo.sourceDirectory = srcDir
            mojo.sourceDocumentName = 'processors-sample.adoc'
            mojo.outputDirectory = outputDir
            mojo.headerFooter = true
            mojo.attributes['toc'] = null
            mojo.attributes['linkcss!'] = ''
            mojo.processors = [
                [className: 'non.existent.Processor'] as ProcessorConfiguration
            ]
            mojo.execute()
        then:
            outputDir.list().size() == 0
            def e = thrown(MojoExecutionException)
            e.message.contains(mojo.processors.get(0).className)
            e.message.contains('not found in classpath')
    }
    
    // This test is added to keep track of possible changes in the extension's SPI
    def "plugin fails because processor throws an uncached exception"() {
        setup:
            File srcDir = new File(SRC_DIR)
            File outputDir = new File("${OUTPUT_DIR}/preprocessor/${System.currentTimeMillis()}")
    
            if (!outputDir.exists())
                outputDir.mkdir()
        when:
            AsciidoctorMojo mojo = new AsciidoctorMojo()
            mojo.backend = 'html'
            mojo.sourceDirectory = srcDir
            mojo.sourceDocumentName = 'processors-sample.adoc'
            mojo.outputDirectory = outputDir
            mojo.headerFooter = true
            mojo.attributes['toc'] = null
            mojo.attributes['linkcss!'] = ''
            mojo.processors = [
                [className: FailingPreprocessor.class.canonicalName] as ProcessorConfiguration
            ]
            mojo.execute()
        then:
            outputDir.list().size() == 0
            thrown(RaiseException)
//            e.message.contains(mojo.processors.get(0).className)
//            e.message.contains('not found in classpath')
    }
    

    /**
     * Redirects output to validate specific traces left in the processors  
     */
    @Unroll
    def "tests that a #processorType is registered, initialized and executed"() {

        setup:
            ByteArrayOutputStream systemOut = new ByteArrayOutputStream()
            System.out = new PrintStream(systemOut)
    
            File srcDir = new File(SRC_DIR)
            File outputDir = new File("${OUTPUT_DIR}/processors/${System.currentTimeMillis()}")
    
            if (!outputDir.exists())
                outputDir.mkdir()
    
            AsciidoctorMojo mojo = new AsciidoctorMojo()
            mojo.backend = 'html'
            mojo.sourceDirectory = srcDir
            mojo.sourceDocumentName = 'processors-sample.adoc'
            mojo.outputDirectory = outputDir
            mojo.headerFooter = true
            mojo.attributes['toc'] = null
            mojo.processors = [
                [className: "org.asciidoctor.maven.test.processors.${processorClass}"] as ProcessorConfiguration
            ]
            mojo.execute()

        expect:
            systemOut.toString().contains(initializationMessage)
            systemOut.toString().contains(executionMessage)

            where:
            processorClass          | processorType           || initializationMessage                                        || executionMessage
            'ChangeAttributeValuePreprocessor'|'Preprocessor' || "ChangeAttributeValuePreprocessor(Preprocessor) initialized" || 'Processing ChangeAttributeValuePreprocessor'
            'DummyTreeprocessor'    |'Treeprocessor'          || "DummyTreeprocessor(Treeprocessor) initialized"              || 'Processing DummyTreeprocessor'
            'DummyPostprocessor'    |'Postprocessor'          || "DummyPostprocessor(Postprocessor) initialized"              || 'Processing DummyPostprocessor'
            'MetaDocinfoProcessor'  |'DocinfoProcessor'       || "MetaDocinfoProcessor(DocinfoProcessor) initialized"         || 'Processing MetaDocinfoProcessor'
            'UriIncludeProcessor'   |'IncludeProcessor'       || "UriIncludeProcessor(IncludeProcessor) initialized"          || 'Processing UriIncludeProcessor'
    }

    def "successfully renders html with a preprocessor"() {
        setup:
            File srcDir = new File(SRC_DIR)
            File outputDir = new File("${OUTPUT_DIR}/preprocessor/${System.currentTimeMillis()}")
    
            if (!outputDir.exists())
                outputDir.mkdir()
        when:
            AsciidoctorMojo mojo = new AsciidoctorMojo()
            mojo.backend = 'html'
            mojo.sourceDirectory = srcDir
            mojo.sourceDocumentName = 'processors-sample.adoc'
            mojo.outputDirectory = outputDir
            mojo.headerFooter = true
            mojo.attributes['toc'] = null
            mojo.processors = [
                [className: ChangeAttributeValuePreprocessor.class.canonicalName] as ProcessorConfiguration
            ]
            mojo.execute()
        then:
            outputDir.list().toList().isEmpty() == false
            outputDir.list().toList().contains('processors-sample.html')
    
            File sampleOutput = new File(outputDir, 'processors-sample.html')
            sampleOutput.length() > 0
            String text = sampleOutput.getText()
            text.count(ChangeAttributeValuePreprocessor.AUTHOR_NAME) == 2
    }

    def "successfully renders html with a blockprocessor"() {
        setup:
            File srcDir = new File(SRC_DIR)
            File outputDir = new File("${OUTPUT_DIR}/blockprocessor/${System.currentTimeMillis()}")
    
            if (!outputDir.exists())
                outputDir.mkdir()
        when:
            AsciidoctorMojo mojo = new AsciidoctorMojo()
            mojo.backend = 'html'
            mojo.sourceDirectory = srcDir
            mojo.sourceDocumentName = 'processors-sample.adoc'
            mojo.outputDirectory = outputDir
            mojo.headerFooter = true
            mojo.attributes['toc'] = null
            mojo.processors = [
                [className: YellBlockProcessor.class.canonicalName, blockName:'yell'] as ProcessorConfiguration
            ]
            mojo.execute()
        then:
            outputDir.list().toList().isEmpty() == false
            outputDir.list().toList().contains('processors-sample.html')
    
            File sampleOutput = new File(outputDir, 'processors-sample.html')
            sampleOutput.length() > 0
            sampleOutput.getText().contains('The time is now. Get a move on.'.toUpperCase())
    }
    
    def "successfully renders html and adds meta tag with a DocinfoProcessor"() {
        setup:
            File srcDir = new File(SRC_DIR)
            File outputDir = new File("${OUTPUT_DIR}/docinfoProcessor/${System.currentTimeMillis()}")
    
            if (!outputDir.exists())
                outputDir.mkdir()
        when:
            AsciidoctorMojo mojo = new AsciidoctorMojo()
            mojo.backend = 'html'
            mojo.sourceDirectory = srcDir
            mojo.sourceDocumentName = 'processors-sample.adoc'
            mojo.outputDirectory = outputDir
            mojo.headerFooter = true
            mojo.attributes['toc'] = null
            mojo.processors = [
                [className: MetaDocinfoProcessor.class.canonicalName] as ProcessorConfiguration
            ]
            mojo.execute()
        then:
            outputDir.list().toList().isEmpty() == false
            outputDir.list().toList().contains('processors-sample.html')
    
            File sampleOutput = new File(outputDir, 'processors-sample.html')
            sampleOutput.length() > 0
            sampleOutput.text.contains("<meta name=\"author\" content=\"asciidoctor\">")
    }

    def "successfully renders html and modifies output with a BlockMacroProcessor"() {
        setup:
            File srcDir = new File(SRC_DIR)
            File outputDir = new File("${OUTPUT_DIR}/blockMacroProcessor/${System.currentTimeMillis()}")
    
            if (!outputDir.exists())
                outputDir.mkdir()
        when:
            AsciidoctorMojo mojo = new AsciidoctorMojo()
            mojo.backend = 'html'
            mojo.sourceDirectory = srcDir
            mojo.sourceDocumentName = 'processors-sample.adoc'
            mojo.outputDirectory = outputDir
            mojo.headerFooter = true
            mojo.attributes['toc'] = null
            mojo.processors = [
                [className: GistBlockMacroProcessor.class.canonicalName, blockName:'gist'] as ProcessorConfiguration
            ]
            mojo.execute()
        then:
            outputDir.list().toList().isEmpty() == false
            outputDir.list().toList().contains('processors-sample.html')
    
            File sampleOutput = new File(outputDir, 'processors-sample.html')
            sampleOutput.length() > 0
            sampleOutput.text.contains("<script src=\"https://gist.github.com/123456.js\"></script>")
    }

    def "successfully renders html and modifies output with a InlineMacroProcessor"() {
        setup:
            File srcDir = new File(SRC_DIR)
            File outputDir = new File("${OUTPUT_DIR}/inlineMacroProcessor/${System.currentTimeMillis()}")
    
            if (!outputDir.exists())
                outputDir.mkdir()
        when:
            AsciidoctorMojo mojo = new AsciidoctorMojo()
            mojo.backend = 'html'
            mojo.sourceDirectory = srcDir
            mojo.sourceDocumentName = 'processors-sample.adoc'
            mojo.outputDirectory = outputDir
            mojo.headerFooter = true
            mojo.attributes['toc'] = null
            mojo.processors = [
                [className: ManpageInlineMacroProcessor.class.canonicalName, blockName:'man'] as ProcessorConfiguration
            ]
            mojo.execute()
        then:
            outputDir.list().toList().isEmpty() == false
            outputDir.list().toList().contains('processors-sample.html')
    
            File sampleOutput = new File(outputDir, 'processors-sample.html')
            sampleOutput.length() > 0
            sampleOutput.text.contains("<p>See <a href=\"gittutorial.html\">gittutorial</a> to get started.</p>")
    }
    
    def "successfully renders html and modifies output with an IncludeProcessor"() {
        setup:
            File srcDir = new File(SRC_DIR)
            File outputDir = new File("${OUTPUT_DIR}/includeProcessor/${System.currentTimeMillis()}")
    
            if (!outputDir.exists())
                outputDir.mkdir()
        when:
            AsciidoctorMojo mojo = new AsciidoctorMojo()
            mojo.backend = 'html'
            mojo.sourceDirectory = srcDir
            mojo.sourceDocumentName = 'processors-sample.adoc'
            mojo.outputDirectory = outputDir
            mojo.headerFooter = true
            mojo.attributes['toc'] = null
            mojo.processors = [
                [className: UriIncludeProcessor.class.canonicalName] as ProcessorConfiguration
            ]
        mojo.execute()
        then:
            outputDir.list().toList().isEmpty() == false
            outputDir.list().toList().contains('processors-sample.html')
    
            File sampleOutput = new File(outputDir, 'processors-sample.html')
            sampleOutput.length() > 0
            sampleOutput.text.contains("source 'https://rubygems.org'")
    }

    def "executes the same preprocessor twice"() {
        setup:
            ByteArrayOutputStream systemOut = new ByteArrayOutputStream()
            System.out = new PrintStream(systemOut)
            File srcDir = new File(SRC_DIR)
            File outputDir = new File("${OUTPUT_DIR}/preprocessor/${System.currentTimeMillis()}")
    
            if (!outputDir.exists())
                outputDir.mkdir()
        when:
            AsciidoctorMojo mojo = new AsciidoctorMojo()
            mojo.backend = 'html'
            mojo.sourceDirectory = srcDir
            mojo.sourceDocumentName = 'processors-sample.adoc'
            mojo.outputDirectory = outputDir
            mojo.headerFooter = true
            mojo.attributes['toc'] = null
            mojo.processors = [
                [className: ChangeAttributeValuePreprocessor.class.canonicalName] as ProcessorConfiguration,
                [className: ChangeAttributeValuePreprocessor.class.canonicalName] as ProcessorConfiguration
            ]
            mojo.execute()
        then:
            outputDir.list().toList().isEmpty() == false
            outputDir.list().toList().contains('processors-sample.html')
    
            File sampleOutput = new File(outputDir, 'processors-sample.html')
            sampleOutput.length() > 0
            String text = sampleOutput.getText()
            text.count(ChangeAttributeValuePreprocessor.AUTHOR_NAME) == 2
            
            systemOut.toString().count('Processing ChangeAttributeValuePreprocessor') == 2
    }


    // Adding a BlockMacroProcessor or BlockProcessor makes the conversion fail
    def "successfully renders html with Preprocessor, DocinfoProcessor, InlineMacroProcessor and IncludeProcessor"() {
        setup:
            File srcDir = new File(SRC_DIR)
            File outputDir = new File("${OUTPUT_DIR}/processors/${System.currentTimeMillis()}")
    
            if (!outputDir.exists())
                outputDir.mkdir()
        when:
            AsciidoctorMojo mojo = new AsciidoctorMojo()
            mojo.backend = 'html'
            mojo.sourceDirectory = srcDir
            mojo.sourceDocumentName = 'processors-sample.adoc'
            mojo.outputDirectory = outputDir
            mojo.headerFooter = true
            mojo.attributes['toc'] = null
            mojo.processors = [
                // Preprocessor
                [className: 'org.asciidoctor.maven.test.processors.ChangeAttributeValuePreprocessor'] as ProcessorConfiguration,
                // DocinfoProcessor
                [className: 'org.asciidoctor.maven.test.processors.MetaDocinfoProcessor'] as ProcessorConfiguration,
                // InlineMacroProcessor
                [className: 'org.asciidoctor.maven.test.processors.ManpageInlineMacroProcessor', blockName:'man'] as ProcessorConfiguration,
                // IncludeProcessor
                [className: 'org.asciidoctor.maven.test.processors.UriIncludeProcessor'] as ProcessorConfiguration,
            ]
            mojo.execute()
        then:
            outputDir.list().toList().isEmpty() == false
            outputDir.list().toList().contains('processors-sample.html')
    
            File sampleOutput = new File(outputDir, 'processors-sample.html')
            sampleOutput.length() > 0

            String text = sampleOutput.text
            text.count(ChangeAttributeValuePreprocessor.AUTHOR_NAME) == 2
            text.contains("<meta name=\"author\" content=\"asciidoctor\">")
            text.contains("<p>See <a href=\"gittutorial.html\">gittutorial</a> to get started.</p>")
            text.contains("source 'https://rubygems.org'")
    }
    
    // This test is added to keep track of possible changes in the extension's SPI 
    def "fails renders html with Preprocessor, DocinfoProcessor"() {
        setup:
            File srcDir = new File(SRC_DIR)
            File outputDir = new File("${OUTPUT_DIR}/processors/${System.currentTimeMillis()}")
    
            if (!outputDir.exists())
                outputDir.mkdir()
        when:
            AsciidoctorMojo mojo = new AsciidoctorMojo()
            mojo.backend = 'html'
            mojo.sourceDirectory = srcDir
            mojo.sourceDocumentName = 'processors-sample.adoc'
            mojo.outputDirectory = outputDir
            mojo.headerFooter = true
            mojo.attributes['toc'] = null
            mojo.processors = [
                // Preprocessor
                [className: 'org.asciidoctor.maven.test.processors.ChangeAttributeValuePreprocessor'] as ProcessorConfiguration,
                // DocinfoProcessor
                [className: 'org.asciidoctor.maven.test.processors.MetaDocinfoProcessor'] as ProcessorConfiguration,
                // InlineMacroProcessor
                [className: 'org.asciidoctor.maven.test.processors.ManpageInlineMacroProcessor', blockName:'man'] as ProcessorConfiguration,
                // IncludeProcessor
                [className: 'org.asciidoctor.maven.test.processors.UriIncludeProcessor'] as ProcessorConfiguration,
                // BlockMacroProcessor
                [className: 'org.asciidoctor.maven.test.processors.GistBlockMacroProcessor', blockName:'gist'] as ProcessorConfiguration,
                // BlockProcessor
                [className: 'org.asciidoctor.maven.test.processors.YellBlockProcessor', blockName:'yell'] as ProcessorConfiguration
            ]
            mojo.execute()
        then:
            def e = thrown(ClassCastException)
            e.message =~ /org\.jruby\.gen.(.)* cannot be cast to org.jruby.RubyObject/            
    }   

    /**
     *  Manual test to validate automatic extension registration.
     *  To execute, copy org.asciidoctor.extension.spi.ExtensionRegistry to 
     *  /src/test/resources/META-INF/services/ and execute
     */
    @spock.lang.Ignore
    def "property extension"() {
        setup:
            File srcDir = new File(SRC_DIR)
            File outputDir = new File("${OUTPUT_DIR}/preprocessor/${System.currentTimeMillis()}")
    
            if (!outputDir.exists())
                outputDir.mkdir()
        when:
            AsciidoctorMojo mojo = new AsciidoctorMojo()
            mojo.backend = 'html'
            mojo.sourceDirectory = srcDir
            mojo.sourceDocumentName = 'processors-sample.adoc'
            mojo.outputDirectory = outputDir
            mojo.headerFooter = true
            mojo.attributes['toc'] = null
            mojo.attributes['linkcss!'] = ''
            mojo.execute()
        then:
            outputDir.list().toList().isEmpty() == false
            outputDir.list().toList().contains('processors-sample.html')
    
            File sampleOutput = new File(outputDir, 'processors-sample.html')
            sampleOutput.length() > 0
            String text = sampleOutput.getText()
            text.count(ChangeAttributeValuePreprocessor.AUTHOR_NAME) == 2
    }
    
}
