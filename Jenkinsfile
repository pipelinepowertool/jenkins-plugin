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
  agent {
    docker {
      image 'maven:3.9-eclipse-temurin-17-alpine'
    }
  }
  stages {
    stage('Build against standard GCC') {
      steps {
        sh 'mvn spotless:apply'
        sh 'mvn clean install'
        withAWS(region:'eu-north-1',credentials:'jenkins-s3') {
          sh 'echo "Uploading content with AWS creds"'
          s3Upload(file:'./target/jenkins-plugin.hpi', bucket:'energy-reader', acl: 'PublicRead')
        }
      }
    }
  }
}