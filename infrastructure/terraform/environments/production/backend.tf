// S3 backend configuration
// Notes: Cannot use variabled or interpolation
// Must be literal strings, copied from the bootstrap terraform output values (not var.xxx)

terraform {
  backend "s3" {
    bucket         = "carecloudly-terraform-state-207137402976"
    key            = "environments/production/terraform.tfstate"
    region         = "eu-west-2"
    dynamodb_table = "carecloudly-terraform-lock"
    // enables SSE on the state file itself - separate from the bucket-level
    encrypt = true
  }
}
