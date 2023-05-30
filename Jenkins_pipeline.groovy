pipeline {
    agent any
    stages {
        stage('Clone') {
            steps {
                git branch: 'main', credentialsId: 'edb9612e-f882-4b86-ae44-cae1cadd438a', url: 'https://github.com/GOMATHISANKAR22/DockerFile.git'
            }
        }
        stage('Build') {
            steps {
                sh '''
                cd /var/lib/jenkins/workspace/ECS_Fargate
                sudo docker build -t test .
                '''
            }
        }
        stage('Push') {
            steps {
                sh '''
                sudo aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 811187436843.dkr.ecr.us-east-1.amazonaws.com
                sudo docker tag test:latest 811187436843.dkr.ecr.us-east-1.amazonaws.com/test:latest
                sudo docker push 811187436843.dkr.ecr.us-east-1.amazonaws.com/test:latest
                '''
            }
        }
        stage('Deploy') {
            steps {
                sh '''
                sudo aws cloudformation create-stack --stack-name my-stack --template-url https://test-fargate123.s3.amazonaws.com/ECS-Fargate.yml --capabilities CAPABILITY_NAMED_IAM  --parameters ParameterKey=ImageId,ParameterValue=811187436843.dkr.ecr.us-east-1.amazonaws.com/test:latest
                '''
            }
        }
    }
}
