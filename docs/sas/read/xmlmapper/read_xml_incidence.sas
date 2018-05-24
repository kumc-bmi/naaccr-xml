/************************************************************************************************************;
    This program reads a NAACCR XML Incidence data file and computes the frequencies of the Primary Site.
 ************************************************************************************************************/;
filename xmldef 'naaccr-xml-sas-def-180-incidence.map';
filename testdata 'synthetic-data_naaccr-180-incidence_10-tumors.xml';
libname testdata XMLV2 xmlmap=xmldef access=READONLY;

data naaccrdata; 
  set testdata.naaccrdata; 

data patients; 
  set testdata.patients;

data tumors; 
  set testdata.tumors; 

data alldata; 
  merge naaccrdata patients; 
    by naaccrdatakey; 
  
data alldata;
  merge alldata tumors;
    by patientkey;

proc freq; 
    tables primarySite;
run;
