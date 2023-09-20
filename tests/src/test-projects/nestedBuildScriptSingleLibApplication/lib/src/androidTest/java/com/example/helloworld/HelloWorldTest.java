/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.helloworld;

import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.widget.TextView;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HelloWorldTest {
    @Rule public ActivityTestRule<HelloWorld> rule = new ActivityTestRule<>(HelloWorld.class);
    private TextView mTextView;

    @Before
    public void setUp() throws Exception {
        final HelloWorld a = rule.getActivity();
        // ensure a valid handle to the activity has been returned
        Assert.assertNotNull(a);
        mTextView = (TextView) a.findViewById(R.id.text);

    }

    @Test
    @MediumTest
    public void testPreconditions() {
        Assert.assertNotNull(mTextView);
    }
}
