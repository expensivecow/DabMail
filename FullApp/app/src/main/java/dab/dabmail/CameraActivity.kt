/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dab.dabmail

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class CameraActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_camera)

    var bundle :Bundle ?=intent.extras
    var from_email: String? = bundle!!.getString("from_email")
    var to_email: String? = bundle!!.getString("to_email")
    var body_email: String? = bundle!!.getString("body_email")

    Log.d("CameraActivity", from_email)
    Log.d("CameraActivity", to_email)
    Log.d("CameraActivity", body_email)

    var poseActivity: PosenetActivity = PosenetActivity()
    poseActivity.setEmailArguments(from_email, to_email, body_email)
    savedInstanceState ?: supportFragmentManager.beginTransaction()
      .replace(R.id.container, poseActivity)
      .commit()
  }
}
