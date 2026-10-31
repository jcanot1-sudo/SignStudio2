package com.tu.org.signstudio

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MySignsActivity : AppCompatActivity() {
    private lateinit var store: PersonalSignStore
    private lateinit var dynamicStore: DynamicSignStore
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private var labels = emptyList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_signs)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        store = PersonalSignStore(this)
        dynamicStore = DynamicSignStore(this)
        list = findViewById(R.id.listMySigns)
        empty = findViewById(R.id.txtMySignsEmpty)
        findViewById<Button>(R.id.btnTrainNewSign).setOnClickListener {
            startActivity(Intent(this, TrainingActivity::class.java))
        }
        list.setOnItemClickListener { _, _, position, _ ->
            startActivity(Intent(this, TrainingActivity::class.java).putExtra("label", labels[position]))
        }
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val static = store.trainedLabels()
        val dynamic = dynamicStore.trainedLabels()
        labels = (static.keys + dynamic.keys).distinct().sorted()
        val rows = labels.map { label ->
            "$label  •  ${static[label] ?: 0} muestras  •  ${dynamic[label] ?: 0} movimientos"
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        empty.text = if (rows.isEmpty()) "Todavía no has entrenado señas personales." else "Mis señas: ${rows.size}"
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
