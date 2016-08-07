package net.kikuchy.skapt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.tasks.compile.GroovyCompile

/**
 * Created by kikuchy on 2016/08/07.
 */
class AndroidSkaptPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        def variants = null;
        if (project.plugins.findPlugin("com.android.application") || project.plugins.findPlugin("android") ||
                project.plugins.findPlugin("com.android.test")) {
            variants = "applicationVariants";
        } else if (project.plugins.findPlugin("com.android.library") || project.plugins.findPlugin("android-library")) {
            variants = "libraryVariants";
        } else {
            throw new ProjectConfigurationException("The android or android-library plugin must be applied to the project", null)
        }

        def configuration = project.configurations.create("skapt").extendsFrom(project.configurations.compile, project.configurations.provided)
        project.extensions.create("skapt", AndroidSkaptExtension)
        project.afterEvaluate {
            if (project.apt.disableDiscovery() && !project.apt.processors()) {
                throw new ProjectConfigurationException('android-apt configuration error: disableDiscovery may only be enabled in the apt configuration when there\'s at least one processor configured', null);
            }
            project.android[variants].all { variant ->
                configureVariant(project, variant, configuration, project.apt)
            }
        }
    }

    static void configureVariant(def project, def variant, def aptConfiguration, def aptExtension) {
        if (aptConfiguration.empty) {
            project.logger.info("No apt dependencies for configuration ${aptConfiguration.name}");
            return;
        }

        def aptOutputDir = project.file(new File(project.buildDir, "build/tmp/kapt/$variant.name/kotlinGenerated"))
        def aptOutput = new File(aptOutputDir, variant.dirName)

        def javaCompile = variant.hasProperty('javaCompiler') ? variant.javaCompiler : variant.javaCompile

        variant.addJavaSourceFoldersToModel(aptOutput);
        def processorPath = (aptConfiguration + javaCompile.classpath).asPath
        def taskDependency = aptConfiguration.buildDependencies
        if (taskDependency) {
            javaCompile.dependsOn += taskDependency
        }

        def processors = aptExtension.processors()

        javaCompile.options.compilerArgs += [
                '-s', aptOutput
        ]

        if (processors) {
            javaCompile.options.compilerArgs += [
                    '-processor', processors
            ]
        }

        if (!(processors && aptExtension.disableDiscovery())) {
            javaCompile.options.compilerArgs += [
                    '-processorpath', processorPath
            ]
        }

        aptExtension.aptArguments.variant = variant
        aptExtension.aptArguments.project = project
        aptExtension.aptArguments.android = project.android

        javaCompile.options.compilerArgs += aptExtension.arguments()

        javaCompile.doFirst {
            aptOutput.mkdirs()
        }

        // Groovy compilation is added by the groovy-android-gradle-plugin in finalizedBy
        def dependency = javaCompile.finalizedBy;
        def dependencies = dependency.getDependencies(javaCompile);
        for (def dep : dependencies) {
            if (dep instanceof GroovyCompile) {
                if (dep.groovyOptions.hasProperty("javaAnnotationProcessing")) {
                    dep.options.compilerArgs += javaCompile.options.compilerArgs;
                    dep.groovyOptions.javaAnnotationProcessing = true
                }
            }
        }
    }
}
