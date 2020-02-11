/*
Copyright 2009-(CURRENT YEAR) Igor Polevoy

Licensed under the Apache License, Version 2.0 (the "License"); 
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at 

http://www.apache.org/licenses/LICENSE-2.0 

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, 
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and 
limitations under the License. 
*/

package org.javalite.activeweb.freemarker;

import org.javalite.activeweb.*;
import freemarker.template.SimpleHash;
import freemarker.template.TemplateBooleanModel;
import org.javalite.common.Convert;

import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import static org.javalite.common.Util.blank;

/**
 * This is a FreeMarker directive which is registered as  <code>&lt;@form... /&gt;</code> tag.
 * This tag generates an HTML form tag and has functionality specific for ActiveWeb.
 * Like any other ActiveWeb tag, it has ability to pass through any non - ActiveWeb attributes. This means that if you
 * specify any attribute that is not mentioned here, it will be passed through as a regular HTML attribute.
 *
 * <p/>
 * Attributes:
 * <ul>
 * <li><strong>controller</strong> - name of a controller to post this form to. Optional. If this attribute is not provided,
 * the tag will find a current controller in context which was used to generate a data for the current view and uses it.  It makes it
 * convenient to write many views for the same controller.</li>
 * <li><strong>action</strong> - name of an action to post this form to.This is different from  regular
 * HTML form@action attribute, as controller, action and id attributes will be used to form an appropriate HTML form action
 * value.  Optional. </li>
 * <li><strong>id</strong> - value of URI "id". Used as URI "id" in forming an HTML Form
 * action attribute, such as: <code>&lt;form action="controller/action/id"</code>. Do not confuse with HTML element ID.
 * Optional.</li>
 * <li><strong>html_id</strong> - value of HTML Form element ID, as in <code>&lt;form id="123..."</code>. Optoinal.</li>
 * <li><strong>method</strong> - this is an HTTP method. Allowed values: GET (default), POST, PUT, DELETE.
 * In case, the values are "put" or "delete", additional hidden input names "_method" will be generated, and
 * the actual HTML method will be set to "post". This workaround is necessary because browsers still do not support
 * PUT and DELETE. Optional. </li>
 * </ul>
 *
 *
 * This tag also is REST-aware, and will generate appropriate formats for HTML Form tag action value depending if the
 * controller is RESTful or not, see {@link org.javalite.activeweb.annotations.RESTful} for more information.
 *
 * <p/>
 * Examples (given that the current context is "simple_context"):
 * <h3>Simple form</h3>
 * code:
 * <pre>
 * &lt;@form controller="simple" action="index" method="get"/&gt;
 * </pre>
 * will generate this HMTL:
 * <pre>
 * &lt;form action="/simple_context/simple/index" method="get"/&gt;
 * </pre>
 *
 * <h3>POST form with ID</h3>
 * code:
 * <pre>
 * &lt;@form controller="simple" action="index" id="123" method="post" html_id="formA"/&gt;
 * </pre>
 * will generate:
 * <pre>
 * &lt;form action="/simple_context/simple/index/123" method="post" id="formA"/&gt;
 *</pre>
 *
 * <h3>Put form</h3>
 * code:
 * <pre>
 *  &lt;@form controller="simple" action="index" method="put"&gt;
 *       &lt;input type="hidden" name="blah"&gt;
 *  &lt;/@form&gt;
 * </pre>
 * will generate this HMTL:
 * <pre>
 * &lt;form action="/simple_context/simple/index" method="post"&gt;
 *       &lt;input type='hidden' name='_method' value='put' /&gt;
 *       &lt;input type="hidden" name="blah"&gt;
 * &lt;/form&gt;
 * </pre>
 *
 * <h3>Put form for RESTful controller</h3>
 * code:
 * <pre>
 *&lt;@form controller="photos"  id="x123" method="put" html_id="formA"&gt;
 *       &lt;input type="hidden" name="blah"&gt;
 * &lt;/@form&gt;
 *</pre>
 * will generate:
 * <pre>
 *  &lt;form action="/simple_context/photos/x123" method="post" id="formA"&gt;
 *       &lt;input type='hidden' name='_method' value='put' /&gt;
 *       &lt;input type="hidden" name="blah"&gt;
 *  &lt;/form&gt;
 </pre>

 * <h4>Adding HTML5-style attributes</h4>
 *  Use a special attribute "data", whose value will be added to the resulting tag verbatim.
 *
 *<pre>
 &lt;@form data="data-greeting='hola' data-bye='astalavista'" ...  &gt;
 *</pre>
 *
 * @author Igor Polevoy
 */
public class FormTag  extends FreeMarkerTag{
    @Override
    protected void render(Map params, String body, Writer writer) throws Exception {

        SimpleHash activeweb = (SimpleHash) get("activeweb");
        if(activeweb == null || !(params.containsKey("controller") || activeweb.toMap().containsKey("controller")))
            throw  new ViewException("could not render this form, controller is not found");


        String httpMethod = Convert.toString(params.get("method"));
        if (httpMethod != null) {
            httpMethod = httpMethod.toLowerCase();
        }

        boolean putOrDelete = "put".equals(httpMethod) || "delete".equals(httpMethod);

        String bodyPrefix = "";

        if (CSRF.verificationEnabled() && (putOrDelete || "post".equals(httpMethod))) {
            bodyPrefix = "\n\t<input type='hidden' name='" + CSRF.name() + "' value='" + CSRF.token() + "' />";
        }

        if(putOrDelete){
            bodyPrefix += "\n\t<input type='hidden' name='_method' value='" + httpMethod + "' />";
            httpMethod = "post";
        }

        if(blank(body)){
            body = "&nbsp;";
        }

        TagFactory tf = new TagFactory("form", bodyPrefix + body);

        String action = Convert.toString(params.get("action"));

        Boolean restful = null;

        String controllerPath = Convert.toString(params.get("controller"));
        if (controllerPath == null) {
            controllerPath = activeweb.get("controller").toString();
            restful = ((TemplateBooleanModel)activeweb.get("restful")).getAsBoolean();
        }

        if(restful == null){// using current controller
            AppController controllerInstance = (AppController) Class.forName(
                ControllerFactory.getControllerClassName(controllerPath)
            ).getDeclaredConstructor().newInstance();

            restful = controllerInstance.restful();
        }

        String id = Convert.toString(params.get("id"));

        String formAction = Router.generate(controllerPath, action, id, restful, new HashMap());
        tf.attribute("action", getContextPath() + formAction);

        tf.attribute("method", httpMethod);
        tf.attribute("id", Convert.toString(params.get("html_id")));

        tf.addAttributesExcept(params, "controller", "action", "method", "id", "html_id", "data");
        tf.textAttributes(Convert.toString(params.get("data")));

        tf.write(writer);
    }
}
