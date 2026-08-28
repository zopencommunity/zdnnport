node('linux') {
  stage ('Poll') {
    checkout([
      $class: 'GitSCM', branches: [[name: '*/main']], extensions: [],
      userRemoteConfigs: [[url: 'https://github.com/zopencommunity/zdnnport.git']]])
  }
  stage('Build') {
    build job: 'Port-Pipeline', parameters: [
      string(name: 'PORT_GITHUB_REPO', value: 'https://github.com/zopencommunity/zdnnport.git'),
      string(name: 'PORT_DESCRIPTION', value: 'IBM z Deep Neural Network (zDNN) Library for NNPA acceleration'),
      string(name: 'BUILD_LINE', value: 'DEV')
    ]
  }
}
