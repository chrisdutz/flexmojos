package org.sonatype.flexmojos.plugin.compiler;

import static org.sonatype.flexmojos.plugin.common.FlexExtension.SWF;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.sonatype.flexmojos.compiler.ICommandLineConfiguration;
import org.sonatype.flexmojos.compiler.MxmlcConfigurationHolder;
import org.sonatype.flexmojos.compiler.command.Result;
import org.sonatype.flexmojos.plugin.compiler.attributes.converter.Module;
import org.sonatype.flexmojos.plugin.utilities.SourceFileResolver;
import org.sonatype.flexmojos.test.util.PathUtil;
import org.sonatype.flexmojos.truster.FlashPlayerTruster;

/**
 * <p>
 * Goal which compiles the Flex sources into an application for either Flex or AIR depending on the package type.
 * </p>
 * <p>
 * The Flex Compiler plugin compiles all ActionScript sources. It can compile the source into 'swf' files. The plugin
 * supports 'swf' packaging.
 * </p>
 * 
 * @author Marvin Herman Froeder (velo.br@gmail.com)
 * @since 4.0
 * @goal compile-swf
 * @requiresDependencyResolution compile
 * @phase compile
 * @configurator flexmojos
 */
public class MxmlcMojo
    extends AbstractMavenFlexCompilerConfiguration<MxmlcConfigurationHolder, MxmlcMojo>
    implements ICommandLineConfiguration, Mojo
{

    /**
     * DOCME Again, undocumented by adobe
     * <p>
     * Equivalent to -file-specs
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;fileSpecs&gt;
     *   &lt;fileSpec&gt;???&lt;/fileSpec&gt;
     *   &lt;fileSpec&gt;???&lt;/fileSpec&gt;
     * &lt;/fileSpecs&gt;
     * </pre>
     * 
     * @parameter
     */
    private List<String> fileSpecs;

    /**
     * A list of resource bundles to include in the output SWC
     * <p>
     * Equivalent to -include-resource-bundles
     * </p>
     * Usage:
     * 
     * <pre>
     * &lt;includeResourceBundles&gt;
     *   &lt;rb&gt;SharedResources&lt;/rb&gt;
     *   &lt;rb&gt;Collections&lt;/rb&gt;
     * &lt;/includeResourceBundles&gt;
     * </pre>
     * 
     * @parameter
     */
    private List<String> includeResourceBundles;

    /**
     * @parameter
     */
    private Module[] modules;

    /**
     * DOCME Another, undocumented by adobe
     * <p>
     * Equivalent to -projector
     * </p>
     * 
     * @parameter expression="${flex.projector}"
     */
    private String projector;

    /**
     * The file to be compiled. The path must be relative with source folder
     * 
     * @parameter expression="${flex.sourceFile}"
     */
    private String sourceFile;

    /**
     * @component
     * @required
     * @readonly
     */
    private FlashPlayerTruster truster;

    /**
     * When true, flexmojos will register register every compiled SWF files as trusted. These SWF files are assigned to
     * the local-trusted sandbox. They can interact with any other SWF files, and they can load data from anywhere,
     * remote or local. On false nothing is done, so if the file is already trusted it will still as it is.
     * 
     * @parameter default-value="true" expression="${updateSecuritySandbox}"
     */
    private boolean updateSecuritySandbox;

    public final Result doCompile( MxmlcConfigurationHolder cfg, boolean synchronize )
        throws Exception
    {
        try
        {
            return compiler.compileSwf( cfg, synchronize );
        }
        finally
        {
            if ( isUpdateSecuritySandbox() )
            {
                truster.updateSecuritySandbox( PathUtil.getCanonicalFile( cfg.getConfiguration().getOutput() ) );
            }
        }

    }

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( !PathUtil.exist( compileSourceRoots ))
        {
            getLog().warn( "Skipping compiler, source path doesn't exist." );
            return;
        }

        executeCompiler( new MxmlcConfigurationHolder( this, getSourceFile() ), true );

        if ( localesRuntime != null )
        {
            List<Result> results = new ArrayList<Result>();
            for ( String locale : localesRuntime )
            {
                MxmlcMojo cfg = this.clone();
                configureResourceBundle( locale, cfg );
                results.add( executeCompiler( new MxmlcConfigurationHolder( cfg, null ), fullSynchronization ) );
            }

            wait( results );
        }

        if ( modules != null )
        {
            List<Result> results = new ArrayList<Result>();

            for ( Module module : modules )
            {
                File moduleSource =
                    SourceFileResolver.resolveSourceFile( project.getCompileSourceRoots(), module.getSourceFile() );

                String classifier = FilenameUtils.getBaseName( moduleSource.getName() ).toLowerCase();

                String moduleFinalName;
                if ( module.getFinalName() != null )
                {
                    moduleFinalName = module.getFinalName();
                }
                else
                {
                    moduleFinalName = project.getBuild().getFinalName() + "-" + classifier;
                }

                File moduleOutputDir;
                if ( module.getDestinationPath() != null )
                {
                    moduleOutputDir = new File( project.getBuild().getDirectory(), module.getDestinationPath() );
                }
                else
                {
                    moduleOutputDir = new File( project.getBuild().getDirectory() );
                }

                // TODO include the original extern
                String[] loadExterns = new String[] { getLinkReport() };

                MxmlcMojo cfg = this.clone();
                cfg.classifier = classifier;
                cfg.targetDirectory = moduleOutputDir;
                cfg.finalName = moduleFinalName;
                if ( module.isOptimize() )
                {
                    cfg.loadExterns = PathUtil.getFiles( loadExterns );
                }
                results.add( executeCompiler( new MxmlcConfigurationHolder( cfg, moduleSource ), fullSynchronization ) );
            }

            wait( results );
        }
    }

    public List<String> getFileSpecs()
    {
        return fileSpecs;
    }

    public List<String> getIncludeResourceBundles()
    {
        return includeResourceBundles;
    }

    public String getProjector()
    {
        return projector;
    }

    @Override
    public final String getProjectType()
    {
        return SWF;
    }

    protected File getSourceFile()
    {
        return SourceFileResolver.resolveSourceFile( project.getCompileSourceRoots(), sourceFile, project.getGroupId(),
                                                     project.getArtifactId() );
    }

    public boolean isUpdateSecuritySandbox()
    {
        return updateSecuritySandbox;
    }

}