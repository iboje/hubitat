/* Tile Template Device Manager
 *
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *     Date              Who           Description
 *    ===========       ===========   =====================================================
 *    2022-08-26        thebearmay    add @name to pull display name
 *    2022-08-29        thebearmay    populate attribute on save, add refreshSlot from child
 *    2022-08-30        thebearmay    add file list for templates
*/

static String version()	{  return '0.0.4'  }


definition (
	name: 			"Tile Template Device Manager", 
	namespace: 		"thebearmay", 
	author: 		"Jean P. May, Jr.",
	description: 	"Use a template file to generate an HTML element for any device.",
	category: 		"Utility",
	importUrl:"https://raw.githubusercontent.com/thebearmay/hubitat/main/tileTemplate/ttDevMgr.groovy",
    installOnOpen:  true,
	oauth: 			false,
    iconUrl:        "",
    iconX2Url:      ""
) 

preferences {
    page name: "mainPage"
    page name: "templateSelect"
}

void installed() {
//	log.trace "installed()"
    state?.isInstalled = true
    initialize()
}

void updated(){
//	log.trace "updated()"
    if(!state?.isInstalled) { state?.isInstalled = true }
	if(debugEnable) runIn(1800,logsOff)
}

void initialize(){
}

void logsOff(){
     app.updateSetting("debugEnable",[value:"false",type:"bool"])
}

def mainPage(){
    dynamicPage (name: "mainPage", title: "", install: true, uninstall: true) {
      	if (app.getInstallationState() == 'COMPLETE') {   
	    	section("Main") {
                state.saveReq = false
                input "qryDevice", "capability.*", title: "Devices of Interest:", multiple: true, required: true, submitOnChange: true
                if (qryDevice != null){        
                        href "templateSelect", title: "Assign Templates to Devices", required: false
                }
                input "clearSettings", "button", title: "Clear previous settings"
                if(state?.clearAll == true) {
                    unsubscribe()
                    settings.each {
                        if(it.key != 'isInstalled') {
                            app.removeSetting("${it.key}")
                        }
                    }
                    state.clearAll = false
                }
                if(!this.getChildDevice("ttdm${app.id}"))
                    addChildDevice("thebearmay","Generic HTML Device","ttdm${app.id}", [name: "HTML Tile Device${app.id}", isComponent: true, label:"HTML Tile Device${app.id}"])                
                input "security", "bool", title: "Hub Security Enabled", defaultValue: false, submitOnChange: true, width:4
                if (security) { 
                    input("username", "string", title: "Hub Security Username", required: false)
                    input("password", "password", title: "Hub Security Password", required: false)
                }
            }
             section("Change Application Name", hideable: true, hidden: true){
               input "nameOverride", "text", title: "New Name for Application", multiple: false, required: false, submitOnChange: true, defaultValue: app.getLabel()
               if(nameOverride != app.getLabel) app.updateLabel(nameOverride)
             }  
	    } else {
		    section("") {
			    paragraph title: "Click Done", "Please click Done to install app before continuing"
		    }
	    }
    }
}

def templateSelect(){
    dynamicPage (name: "templateSelect", title: "Template Assignments", install: false, uninstall: false) {
	  section(""){
          unsubscribe()
          int i = 1
          List<String> fList = listFiles()
          qryDevice.each{
              input "template${it.deviceId}", "enum", title: "<b>Template for $it</b>", required: false, width:5, submitOnUpdate:true, options:fList
              input "slot${it.deviceId}","number", title:"<b>Slot Number</b>", required: false, width:5, defaultValue:i, submitOnUpdate:true
              if(settings["template${it.deviceId}"] != null && settings["slot${it.deviceId}"] != null) {
                  subscribe(it, "altHtml", [filterEvents:true])
                  //log.debug "${it.deviceId}"
                  altHtml([deviceId:it.deviceId])
              }
              i++
          }
      }
    }
}

void altHtml(evt) {
    log.debug "Device Id ${evt.deviceId}"

    qryDevice.each{
        if(it.deviceId == evt.deviceId) dev=it
    }
    log.debug "reading template ${settings["template${evt.deviceId}"]}"
    
    String fContents = readFile("${settings["template${evt.deviceId}"]}")
    List fRecs=fContents.split("\n")
    String html = ""
    fRecs.each {
        int vCount = it.count("<%")
        if(debugEnable) log.debug "variables found: $vCount"
        if(vCount > 0){
            recSplit = it.split("<%")
            if(debugEnable) log.debug "$recSplit"
            recSplit.each {
                if(it.indexOf("%>") == -1)
                    html+= it
                else {
                    vName = it.substring(0,it.indexOf('%>'))
                    if(debugEnable) log.debug "${it.indexOf("5>")}<br>$it<br>${it.substring(0,it.indexOf("%>"))}"
                    if(vName == "date()" || vName == "@date")
                        aVal = new Date()
                    else if (vName == "@version")
                        aVal = version()
                    else if (vName == "@name")
                        aVal = dev.properties.displayName
                    else {
                        aVal = dev.currentValue("$vName",true)
                        String attrUnit = dev.currentState("$vName")?.unit
                        if (attrUnit != null) aVal+=" $attrUnit"
                    }
                    html+= aVal
                    if(it.indexOf("%>")+2 != it.length()) {
                        if(debugEnable) log.debug "${it.substring(it.indexOf("%>")+2)}"
                        html+=it.substring(it.indexOf("%>")+2)
                    }
                }                 
            }
        }
        else html += it
    }
    if (debugEnable) log.debug html
    chd = getChildDevice("ttdm${app.id}")
    slotNum = settings["slot${evt.deviceId}"]
    log.debug "storing html$slotNum\n$html"
    chd.sendEvent(name:"html$slotNum", value:html)
}

void refreshSlot(sNum) {
    settings.each {
        if(it.key.indexOf("slot") > -1){
            log.debug "${it.key} ${it.value} $sNum" 
            if(it.value == sNum){
                log.debug "calling altHtml deviceId:${it.key.substring(4).toLong()}"
                altHtml([deviceId:it.key.substring(4).toLong()])
            }
        }
    }
}

@SuppressWarnings('unused')
String readFile(fName){
    if(security) cookie = getCookie()
    uri = "http://${location.hub.localIP}:8080/local/${fName}"

    def params = [
        uri: uri,
        contentType: "text/html",
        textParser: true,
        headers: [
				"Cookie": cookie,
                "Accept": "application/octet-stream"
            ]
    ]

    try {
        httpGet(params) { resp ->
            if(resp!= null) {       
               int i = 0
               String delim = ""
               i = resp.data.read() 
               while (i != -1){
                   char c = (char) i
                   delim+=c
                   i = resp.data.read() 
               }
               if(debugEnabled) log.info "File Read Data: $delim"
               return delim
            }
            else {
                log.error "Null Response"
            }
        }
    } catch (exception) {
        log.error "Read Error: ${exception.message}"
        return null;
    }
}

@SuppressWarnings('unused')
List<String> listFiles(){
    if(security) cookie = getCookie()
    // Adapted from BptWorld's Community Post 89466/4
    if(debugEnabled) log.debug "Getting list of files"
    uri = "http://${location.hub.localIP}:8080/hub/fileManager/json";
    def params = [
        uri: uri,
        headers: [
				"Cookie": cookie
            ]        
    ]
    try {
        fileList = []
        httpGet(params) { resp ->
            if (resp != null){
                if(logEnable) log.debug "Found the files"
                def json = resp.data
                for (rec in json.files) {
                    fileList << rec.name
                }
            } else {
                //
            }
        }
        if(debugEnabled) log.debug fileList.sort()
        return fileList.sort()
    } catch (e) {
        log.error e
    }
}

@SuppressWarnings('unused')
String getCookie(){
    try{
  	  httpPost(
		[
		uri: "http://127.0.0.1:8080",
		path: "/login",
		query: [ loginRedirect: "/" ],
		body: [
			username: username,
			password: password,
			submit: "Login"
			]
		]
	  ) { resp -> 
		cookie = ((List)((String)resp?.headers?.'Set-Cookie')?.split(';'))?.getAt(0) 
        if(debugEnable)
            log.debug "$cookie"
	  }
    } catch (e){
        cookie = ""
    }
    return "$cookie"

}

def appButtonHandler(btn) {
    switch(btn) {
        case "clearSettings":
            state.clearAll = true
            break
        case "saveTemplate":
            if(saveAs == null) break
            state.saveReq = true
            break
        default: 
            log.error "Undefined button $btn pushed"
            break
    }
}

void intialize() {

}
