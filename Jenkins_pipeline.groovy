pipeline {
    agent any
    stages {
        stage('Clone') {
            steps {
                git branch: 'main', credentialsId: 'edb9612e-f882-4b86-ae44-cae1cadd438a', url: 'https://github.com/GOMATHISANKAR22/DockerFile.git'
            }
        }
        stage('Copy to S3') {
            steps {
                sh 'aws s3 cp /var/lib/jenkins/workspace/test1/ECS-Fargate.yml s3://s3.artifactjenkins/'
            }
        }
        stage('Build') {
            steps {
                sh '''
                cd /var/lib/jenkins/workspace/test2
                sudo docker build -t test1 .
                '''
            }
        }
        stage('Push') {
            steps {
                sh '''
                sudo aws ecr create-repository --repository-name test1 --region us-east-1 || true  # Ignore error if repo already exists    
                sudo aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 811187436843.dkr.ecr.us-east-1.amazonaws.com
                sudo docker tag test1:latest 811187436843.dkr.ecr.us-east-1.amazonaws.com/test1:latest
                sudo docker push 811187436843.dkr.ecr.us-east-1.amazonaws.com/test1:latest
                '''
            }
        }
        stage('Check Stack Existence') {
            steps {
                script {
                    def stackExists = sh(
                        returnStatus: true,
                        script: 'aws cloudformation describe-stacks --stack-name my-stack'
                    )
                    if (stackExists == 0) {
                        
                        script {
                            sh '''
                            sudo aws cloudformation update-stack --stack-name my-stack --template-url https://s3.artifactjenkins.s3.amazonaws.com/ECS-Fargate.yml --capabilities CAPABILITY_NAMED_IAM  --parameters ParameterKey=ImageId,ParameterValue=811187436843.dkr.ecr.us-east-1.amazonaws.com/test1:latest
                            '''
                        }
                    } else {
                      
                        script {
                            sh '''
                            sudo aws cloudformation create-stack --stack-name my-stack --template-url https://s3.artifactjenkins.s3.amazonaws.com/ECS-Fargate.yml --capabilities CAPABILITY_NAMED_IAM  --parameters ParameterKey=ImageId,ParameterValue=811187436843.dkr.ecr.us-east-1.amazonaws.com/test1:latest
                            '''
                        }
                    }
                }
            }
        }
    }
}


        
