def NAME = "dropwizard-swagger"
def EMAIL = "_Architecture@kenshoo.com"

job("${NAME}-pull-request") {
    label("microcosm-centos7")
    jdk('OpenJDK-11')

    logRotator(10,10)
    concurrentBuild(true)
    throttleConcurrentBuilds{
        maxPerNode 1
        maxTotal 10
    }

    scm {
        git {
            remote {
                url("git@github.com:kenshoo/${NAME}.git")
                credentials('kgithub-build-jenkins-microcosm-key')
                refspec('+refs/pull/*:refs/remotes/origin/pr/*')
            }

            configure { node ->
                node / 'extensions' / 'hudson.plugins.git.extensions.impl.CleanBeforeCheckout' {}
            }

            branch("\${sha1}")
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
    }

    triggers {
        githubPullRequest {
            orgWhitelist('Kenshoo')
            useGitHubHooks()
        }
    }

    steps {
        shell("""
            rm -f ~/.m2/settings.xml
            ulimit -c unlimited -S
            sudo yum -y install maven
            mvn -N io.takari:maven:wrapper
            ./mvnw clean install
            """)
    }

    publishers {
        archiveJunit('**/surefire-reports/TEST-*.xml, **/surefire-reports/*/TEST-*.xml')
        extendedEmail {
            recipientList("${EMAIL}")
            triggers {
                unstable {
                    sendTo {
                        requester()
                        developers()
                    }
                }
                failure {
                    sendTo {
                        requester()
                        developers()
                    }
                }
                statusChanged {
                    sendTo {
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
