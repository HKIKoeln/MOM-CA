xquery version "3.0";

import module namespace fuego="https://github.com/ept/fuego-diff";

let $base-version := <test><a/><b/><c/><e/></test>
let $modified-version := <test><f/></test>
return
    fuego:diff($base-version, $modified-version)