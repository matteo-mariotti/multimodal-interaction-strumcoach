package com.example.strumcoach.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.strumcoach.Exercise

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateExerciseDialog(
    initialIsSong: Boolean = false,
    exerciseToEdit: Exercise? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean, String, String) -> Unit
) {
    var name by remember { mutableStateOf(exerciseToEdit?.name ?: "") }
    var pattern by remember { mutableStateOf(exerciseToEdit?.strummingPattern ?: "") }
    var difficulty by remember { mutableStateOf(exerciseToEdit?.difficulty ?: "Easy") }
    var isSong by remember { mutableStateOf(exerciseToEdit?.isSong ?: initialIsSong) }
    var expanded by remember { mutableStateOf(false) }
    val levels = listOf("Easy", "Medium", "Hard")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exerciseToEdit != null) "Edit Exercise" else "New Exercise") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern (e.g. D D U U)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = difficulty,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Difficulty") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        levels.forEach { level ->
                            val color = getDifficultyColor(level)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(level, fontWeight = FontWeight.Bold)
                                    }
                                },
                                onClick = {
                                    difficulty = level
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Song", modifier = Modifier.weight(1f))
                    Switch(checked = isSong, onCheckedChange = { isSong = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, isSong, pattern, difficulty) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
