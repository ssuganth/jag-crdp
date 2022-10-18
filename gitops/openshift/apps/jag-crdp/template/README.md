## Templates to create openshift components related to jag-crdp api deployment

### Command to execute template
1) Login to OC using login command
2) Run below command in each env. namespace dev/test/prod
   ``oc process -f jag-crdp.yaml --param-file=jag-crdp.env | oc apply -f -``


