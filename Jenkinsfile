pipeline {
    agent {
        dockerfile {
            dir 'conf/docker/jenkins-build-image'
            additionalBuildArgs '-t drodb-jenkins-build'
        }
    }

    parameters {
        string(name: 'branch', defaultValue: 'develop', description: 'Branch to build', trim: true)
        booleanParam(name: 'skipBuild', defaultValue: true, description: 'Set to true to skip build stage')
        booleanParam(name: 'skipUnitTests', defaultValue: true, description: 'Set to true to skip the unit tests')
        booleanParam(name: 'skipIntegrationTests', defaultValue: true, description: 'Set to true to skip the integration tests')
    }

    triggers {
    	pollSCM('H/10 8-20 * * 1-5')
    }

    options {
        buildDiscarder logRotator(artifactDaysToKeepStr: '3', artifactNumToKeepStr: '3', daysToKeepStr: '3', numToKeepStr: '3')
        disableConcurrentBuilds()
        timeout(activity: true, time: 10)
    }

    environment {
        ARTIFACTORY_DEPLOY=credentials('ICM_ARTIFACTORY_JENKINSCI')
        DOCKER_HOST_EXT = sh(script: 'docker context ls --format "{{- if .Current -}} {{- .DockerEndpoint -}} {{- end -}}"', returnStdout: true)
                            .trim().replaceAll('tcp', 'https')
        DOCKER_CERT_EXT = '/home/jenkins/.docker'
        MAVEN_OPTS = "-Dmaven.repo.local=/home/jenkins/.m2/repository"
    }

    stages {

        stage('Build') {
            when { expression { params.skipBuild != true } }
            agent {
                docker {
                    image 'openjdk:8u342-jdk'
                    reuseNode true
                }
            }
            steps {
               echo 'Building dataverse.'
               sh './mvnw package -DskipTests'
            }

            post {
                always {
                    recordIssues(tools: [mavenConsole(), java()])
            	}
            }
        }

        stage('Unit tests') {
            when { expression { params.skipUnitTests != true } }
            agent {
                docker {
                    image 'openjdk:8u342-jdk'
                    reuseNode true
                }
            }
            steps {
               echo 'Executing unit tests.'
               sh './mvnw test'
            }

            post {
                always {
                    junit skipPublishingChecks: true, testResults: '**/target/surefire-reports/*.xml'
            		jacoco()
            	}
            }
        }

        stage('Integration tests') {
            when { expression { params.skipIntegrationTests != true } }
            steps {
                script {
                    try {
                        networkId = UUID.randomUUID().toString()
                        sh "docker network inspect ${networkId} >/dev/null 2>&1 || docker network create --driver bridge ${networkId}"
                        env.DOCKER_NETWORK_NAME = "${networkId}"

                        docker.image('openjdk:8u342-jdk').inside("--network ${networkId}") { c ->
                            echo 'Executing integration tests.'
                            sh './mvnw verify -P integration-tests-only,ci-jenkins -Dtest.network.name=$DOCKER_NETWORK_NAME -Ddocker.host=$DOCKER_HOST_EXT -Ddocker.certPath=$DOCKER_CERT_EXT'
                        }
                    } finally {
                        sh "docker network rm ${networkId}"
                    }
                }
            }

            post {
                always {
                    junit skipPublishingChecks: true, testResults: '**/target/failsafe-reports/*.xml'
            	}
            }
        }

        stage('Deploy') {
            when { expression { params.branch == 'develop' } }
            agent {
                docker {
                    image 'openjdk:8u342-jdk'
                    reuseNode true
                }
            }
            steps {
               echo 'Deploying artifacts.'
               sh './mvnw deploy:deploy -s settings.xml'
            }
        }
    }
}

