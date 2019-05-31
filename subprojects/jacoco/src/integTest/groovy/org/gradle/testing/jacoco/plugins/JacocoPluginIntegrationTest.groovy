/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.testing.jacoco.plugins

import org.gradle.api.Project
import org.gradle.api.reporting.ReportingExtension
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testing.jacoco.plugins.fixtures.JavaProjectUnderTest

class JacocoPluginIntegrationTest extends AbstractIntegrationSpec {

    private final JavaProjectUnderTest javaProjectUnderTest = new JavaProjectUnderTest(testDirectory)
    private static final String REPORTING_BASE = "${Project.DEFAULT_BUILD_DIR_NAME}/${ReportingExtension.DEFAULT_REPORTS_DIR_NAME}"

    def setup() {
        javaProjectUnderTest.writeBuildScript().writeSourceFiles()
    }

    def "does not add jvmArgs if jacoco is disabled"() {
        buildFile << """
            test {
                jacoco {
                    enabled = false
                }

                doLast {
                    assert allJvmArgs.every { !it.contains("javaagent") }
                }
            }
        """
        expect:
        succeeds "test"
    }

    def "jacoco plugin adds coverage report for test task when java plugin applied"() {
        given:
        buildFile << '''
            task doCheck {
                doLast {
                    assert project.test.extensions.getByType(JacocoTaskExtension) != null
                    assert project.jacocoTestReport instanceof JacocoReport
                    assert project.jacocoTestReport.sourceDirectories*.absolutePath == project.layout.files("src/main/java")*.absolutePath
                    assert project.jacocoTestReport.classDirectories*.absolutePath == project.sourceSets.main.output*.absolutePath
                }
            }
        '''.stripIndent()

        expect:
        succeeds 'doCheck'
    }

    def "dependencies report shows default jacoco dependencies"() {
        when: succeeds("dependencies", "--configuration", "jacocoAgent")
        then: output.contains "org.jacoco:org.jacoco.agent:"

        when: succeeds("dependencies", "--configuration", "jacocoAnt")
        then: output.contains "org.jacoco:org.jacoco.ant:"
    }

    void "allows configuring tool dependencies explicitly"() {
        when:
        buildFile << """
            dependencies {
                //downgrade version:
                jacocoAgent "org.jacoco:org.jacoco.agent:0.6.0.201210061924"
                jacocoAnt "org.jacoco:org.jacoco.ant:0.6.0.201210061924"
            }
        """

        succeeds("dependencies", "--configuration", "jacocoAgent")
        then: output.contains "org.jacoco:org.jacoco.agent:0.6.0.201210061924"

        when: succeeds("dependencies", "--configuration", "jacocoAnt")
        then: output.contains "org.jacoco:org.jacoco.ant:0.6.0.201210061924"
    }

    void jacocoReportIsIncremental() {
        def reportResourceDir = file("${REPORTING_BASE}/jacoco/test/html/jacoco-resources")

        when:
        succeeds('test', 'jacocoTestReport')

        then:
        htmlReport().exists()
        reportResourceDir.exists()

        when:
        succeeds('jacocoTestReport')
        executedAndNotSkipped(":jacocoTestReport")

        then:
        skipped(":jacocoTestReport")
        htmlReport().exists()
        reportResourceDir.exists()

        when:
        reportResourceDir.deleteDir()
        succeeds('test', 'jacocoTestReport')

        then:
        executedAndNotSkipped(":jacocoTestReport")
        htmlReport().exists()
        reportResourceDir.exists()
    }

    def "using append is deprecated"() {
        buildFile << """
            test {
                jacoco {
                    append = false
                }
            }
        """
        def deprecationMessage = "The append property has been deprecated. This is scheduled to be removed in Gradle 6.0. Append should always be true."

        when:
        executer.expectDeprecationWarning()
        succeeds("help")
        then:
        output.contains(deprecationMessage)

        when:
        buildFile.text = ""
        javaProjectUnderTest.writeBuildScript()
        buildFile << """
            println test.jacoco.append
        """
        and:
        executer.expectDeprecationWarning()
        succeeds("help")
        then:
        output.contains(deprecationMessage)
    }

    private JacocoReportFixture htmlReport(String basedir = "${REPORTING_BASE}/jacoco/test/html") {
        return new JacocoReportFixture(file(basedir))
    }
}

