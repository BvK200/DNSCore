<%@ page contentType="text/html; charset=UTF-8" %>
<html>
  <head>
    <meta name="layout" content="main" />
    <title>Administrative Funktionen</title>         
  </head>
  <r:require modules="periodicalupdater, jqueryui"/>
		<r:script>
			$(function() {
				$("#legend").accordion({ collapsible: true, active: false, autoHeight: false });
			});
			$(function() {
				$("#filter").accordion({ collapsible: true, active: false });
			});
			
			$.PeriodicalUpdater("./messageSnippet",
				{
					method: "get",
					minTimeout: 1000,
					maxTimeout: 1000,
					success: function(data) {
						console.log("success - sort: "+sort+", order: "+order);
						$("#entry-list").html(data);
					}
				}
			);
		</r:script>
  <body>
  <div class="nav" role="navigation">
			<ul>
				<li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
			</ul>
		</div>
    <div class="body">
      <h1>CbTalk</h1>
      <g:if test="${flash.message}">
        <div class="message">${flash.message}</div>
      </g:if>
      <g:form action="save" method="post">
   <g:submitButton name="stopFactory" value="stop Factory" />
   <g:submitButton name="startFactory" value="start Factory" /> 
   <g:submitButton name="showActions" value="show Actions" /> 
   <g:submitButton name="gracefulShutdown" value="ContentBroker graceful shutdown" />  
   <g:submitButton name="showVersion" value="Show Version of ContentBroker" />    
</g:form>

			<!-- This div is updated through the periodical updater -->
			<div class="list" id="entry-list">
				<g:include action="messageSnippet" />
			</div>
			 Errors:      
<g:each var="Error" in="${errors}">
      	 <p>${Error}</p>
      </g:each> 
     </div>
  </body>
</html>