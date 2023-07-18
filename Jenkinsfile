/*
 See the documentation for more options:
 https://github.com/jenkins-infra/pipeline-library/
*/
// buildPlugin(
//   useContainerAgent: true, // Set to `false` if you need to use Docker for containerized tests
//   configurations: [
//     [platform: 'linux', jdk: 17],
//     [platform: 'windows', jdk: 11],
// ])

pipeline {
//   environment {
//     JAVA_TOOL_OPTIONS = '-Duser.home=/root'
//   }
  agent {
    docker {
      image 'maven:3.9-eclipse-temurin-17-alpine'
      args '-v /home/jenkins:/root'
    }
  }
  stages {
    stage('Release plugin artifact on s3') {
      steps {
        configFileProvider([configFile(fileId: 'ce7257b3-97e2-4486-86ee-428f65c0ff26', variable: 'MAVEN_SETTINGS')]) {
            sh 'mvn -s $MAVEN_SETTINGS clean install'
        }
        withAWS(region:'eu-north-1',credentials:'jenkins-s3') {
          sh 'echo "Uploading content with AWS creds"'
          s3Upload(file:'./target/jenkins-plugin.hpi', bucket:'energy-reader', acl: 'PublicRead')
        }
      }
    }
  }
}