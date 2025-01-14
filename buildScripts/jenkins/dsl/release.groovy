def NAME = "dropwizard-swagger"
def BRANCH_NAME = "master"
def EMAIL = "_Architecture@kenshoo.com"

job("${NAME}-release") {
    label("microcosm-centos7")
    jdk('OpenJDK-11')

    logRotator(-1,10)
    throttleConcurrentBuilds {
        maxPerNode(1)
        maxTotal(3)
    }

    scm {
        git {
            remote {
                url("git@github.com:kenshoo/${NAME}.git")
                credentials('kgithub-build-jenkins-microcosm-key')
                refspec("+refs/heads/${BRANCH_NAME}:refs/remotes/origin/${BRANCH_NAME}")
            }

            configure { node ->
                node / 'extensions' / 'hudson.plugins.git.extensions.impl.CleanBeforeCheckout' {}
            }

            branch("$BRANCH_NAME")
        }
    }

    configure { project ->
        def properties = project / 'properties'
        properties<< {
            'com.coravy.hudson.plugins.github.GithubProjectProperty'{
                projectUrl "https://github.com/kenshoo/${NAME}/"
            }
        }
    }

    wrappers {
        preBuildCleanup()
        timestamps()
        injectPasswords()
        colorizeOutput()
        timeout {
            absolute(10)
        }
        credentialsBinding {
            usernamePassword('MICROSERVICES_ARTIFACTORY_USER', 'MICROSERVICES_ARTIFACTORY_PASSWORD', 'MICROSERVICES_ARTIFACTORY')
        }
    }

    triggers {
        githubPush()
    }

    steps {
        shell("""
            revision=2.0.12.\$BUILD_NUMBER
            rm -f ~/.m2/settings.xml
            ulimit -c unlimited -S
            sudo yum -y install maven
            mvn -N io.takari:maven:wrapper -Drevision=\$revision
            ./mvnw clean install -Drevision=\$revision
            ./mvnw -B deploy --settings maven_deploy_settings.xml -Dmaven.test.skip=true -Dfindbugs.skip=true -Drevision=\$revision
            """)
    }

    publishers {
        archiveJunit('**/surefire-reports/TEST-*.xml, **/surefire-reports/*/TEST-*.xml')
        extendedEmail {
            recipientList("${EMAIL}")
            triggers {
                unstable {
                    sendTo {
                        recipientList()
                        requester()
                        developers()
                    }
                }
                failure {
                    sendTo {
                        recipientList()
                        requester()
                        developers()
                    }
                }
                statusChanged {
                    sendTo {
                        recipientList()
                        requester()
                        developers()
                    }
                }
                configure { node ->
                    node / contentType << 'text/html'
                }
            }
        }
    }
}
