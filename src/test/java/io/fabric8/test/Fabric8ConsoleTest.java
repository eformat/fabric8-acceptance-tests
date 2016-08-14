/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.test;

import net.serenitybdd.junit.runners.SerenityRunner;
import net.thucydides.core.annotations.Managed;
import net.thucydides.core.annotations.Steps;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.WebDriver;


@RunWith(SerenityRunner.class)
public class Fabric8ConsoleTest {

    @Managed(driver="chrome", uniqueSession = true)
    WebDriver driver;

    @Steps
    Fabric8Console console;

    @Test
    public void loginTest() {
        console.open_login_page();
        console.log_in("developer", "developer");
    }
    
/*    @Test
    public void testCreateCamelCDIProjectFromArchetype() throws Exception {
        console.CD_pipeline_is_running();
        console.create_camel_CDI_project();
    }*/
}