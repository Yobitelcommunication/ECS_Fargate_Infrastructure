pipeline {
    agent any
    environment {
        Git_Hub_URL             = 'https://github.com/GOMATHISANKAR22/DockerFile.git'
        Workspace_name          = 'test1'
        Cloudformation_Template = 'ECS-Fargate.yml'
        Bucket_Name             = 'awsstoragedeploy123'
        Image_Name              = 'test1'
        Region_Name             = 'us-east-1'
        Aws_Id                  = '811187436843'
        Stack_Name              = 'my-stack'
    }
    
    stages {
        stage('Clone') {
            steps {
                git branch: 'main', credentialsId: 'edb9612e-f882-4b86-ae44-cae1cadd438a', url: "${Git_Hub_URL}"
            }
        }
        stage('Copy to S3') {
            steps {
                sh '''
                aws s3 mb s3://${Bucket_Name}
                aws s3api put-bucket-versioning --bucket ${Bucket_Name} --versioning-configuration Status=Enabled
                aws s3 cp /var/lib/jenkins/workspace/${Workspace_name}/${Cloudformation_Template} s3://${Bucket_Name}/ 
                '''
            }
        }
        stage('Build') {
            steps {
                sh '''
                cd /var/lib/jenkins/workspace/${Workspace_name}
                sudo docker build -t ${Image_Name} .
                '''
            }
        }
        stage('Push') {
            steps {
                sh '''
                sudo aws ecr create-repository --repository-name ${Image_Name} --region ${Region_Name} || true   
                sudo aws ecr get-login-password --region ${Region_Name} | docker login --username AWS --password-stdin ${Aws_Id}.dkr.ecr.${Region_Name}.amazonaws.com
                sudo docker tag ${Image_Name}:latest ${Aws_Id}.dkr.ecr.${Region_Name}.amazonaws.com/${Image_Name}:latest
                sudo docker push ${Aws_Id}.dkr.ecr.${Region_Name}.amazonaws.com/${Image_Name}:latest 
                '''
            }
        }
        stage('Check Stack Existence') {
            steps {
                script {
                    def stackExists = sh(
                        returnStatus: true,
                        script: 'aws cloudformation describe-stacks --stack-name ${Stack_Name}'
                    )
                    if (stackExists == 0) {
                        script {
                            sh '''
                            sudo aws cloudformation update-stack --stack-name ${Stack_Name} --template-url https://${Bucket_Name}.s3.amazonaws.com/${Cloudformation_Template} --capabilities CAPABILITY_NAMED_IAM  --parameters ParameterKey=ImageUri,ParameterValue=${Aws_Id}.dkr.ecr.${Region_Name}.amazonaws.com/${Image_Name}:latest || true
                            '''
                        }
                    } else {
                        script {
                            sh '''
                            sudo aws cloudformation create-stack --stack-name ${Stack_Name} --template-url https://${Bucket_Name}.s3.amazonaws.com/${Cloudformation_Template} --capabilities CAPABILITY_NAMED_IAM  --parameters ParameterKey=ImageUri,ParameterValue=${Aws_Id}.dkr.ecr.${Region_Name}.amazonaws.com/${Image_Name}:latest
                            '''
                        }
                    }
                }
            }
        }
    }
}


        
      
