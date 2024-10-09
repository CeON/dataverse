GIT_USER_NAME = "jenkinsci"
GIT_USER_EMAIL = "jenkinsci@icm.edu.pl"
RELEASE_CHOICES = ['skip', 'patch', 'minor', 'major']

pipeline {
    agent {
        dockerfile {
            dir 'conf/docker/jenkins-build-dockercli-image'
            additionalBuildArgs '-t drodb-dockercli'
        }
    }

    parameters {
        string(name: 'branch', defaultValue: params.branch ?: 'develop', description: 'Branch to build', trim: true)
        booleanParam(name: 'skipBuild', defaultValue: params.skipBuild ?: false, description: 'Set to true to skip build stage')
        booleanParam(name: 'skipUnitTests', defaultValue: params.skipUnitTests ?: false, description: 'Set to true to skip the unit tests')
        booleanParam(name: 'skipIntegrationTests', defaultValue: params.skipIntegrationTests ?: true, description: 'Set to true to skip the integration tests')
        booleanParam(name: 'deployOverride', defaultValue: params.deployOverride ?: false, description: 'Set to true to perform the deployment')
        choice(
            name: 'doRelease',
            choices: (params.doRelease ? [params.doRelease] : []) +
                        (RELEASE_CHOICES - (params.doRelease ? [params.doRelease] : [])),
            description: 'Perform a release of new version')
    }

    options {
        buildDiscarder logRotator(artifactDaysToKeepStr: '3', artifactNumToKeepStr: '3', daysToKeepStr: '3', numToKeepStr: '3')
        disableConcurrentBuilds()
        timeout(activity: true, time: 10)
    }

    environment {
        ARTIFACTORY_DEPLOY=credentials('ICM_ARTIFACTORY_USER')
        GITHUB_DEPLOY=credentials('DATAVERSE_GORGONA_GITHUB_DEPLOY_KEY')
        DOCKER_HOST_EXT = sh(script: 'docker context ls --format "{{- if .Current -}} {{- .DockerEndpoint -}} {{- end -}}"', returnStdout: true)
                            .trim().replaceAll('tcp', 'https')
        DOCKER_CERT_EXT = '/home/jenkins/.docker'
        MAVEN_OPTS = "-Dmaven.repo.local=/home/jenkins/.m2/repository"
        GIT_SSH_COMMAND = "ssh -o StrictHostKeyChecking=no"
    }

    stages {

        stage('Prepare') {
            agent {
                dockerfile {
                    dir 'conf/docker/jenkins-build-image'
                    additionalBuildArgs '-t drodb-build'
                    reuseNode true
                }
            }

            steps {
               echo 'Preparing build.'
            }
        }

        stage('Build') {
            when { expression { params.skipBuild != true } }
            agent {
                docker {
                    image 'drodb-build:latest'
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
                    image 'drodb-build:latest'
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

                        docker.image('drodb-build:latest').inside("--network ${networkId}") { c ->
                            echo 'Executing integration tests.'
                            sh './mvnw verify -P integration-tests-only,ci-jenkins -Dtest.network.name=$DOCKER_NETWORK_NAME -Ddocker.host=$DOCKER_HOST_EXT -Ddocker.certPath=$DOCKER_CERT_EXT'
                        }
                    } finally {
                        sh "docker network rm -f ${networkId}"
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
            when {
                anyOf {
                    expression { params.branch == 'develop' }
                    expression { params.deployOverride == true }
                }
            }

            agent {
                docker {
                    image 'drodb-build:latest'
                    reuseNode true
                }
            }

            steps {
               echo 'Deploying artifacts.'
               sh './mvnw deploy -Pdeploy -s settings.xml'
            }
        }

        stage('Release') {
            when {
                triggeredBy 'UserIdCause'
                expression { params.doRelease != 'skip' }
            }

            agent {
                docker {
                    image 'drodb-build:latest'
                    reuseNode true
                }
            }

            steps {
                script {
                    sshagent(['DATAVERSE_GORGONA_GITHUB_DEPLOY_KEY']) {
                        echo "Creating release artifacts: ${params.doRelease}"
                        sh "git config user.email ${GIT_USER_EMAIL}"
                        sh "git config user.name ${GIT_USER_NAME}"
                        sh "./release.sh ${params.doRelease}"
                    }
                }
            }
        }

    }
}

