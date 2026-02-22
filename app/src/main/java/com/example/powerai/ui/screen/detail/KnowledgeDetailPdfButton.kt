package com.example.powerai.ui.screen.detail

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.powerai.navigation.Screen
import com.example.powerai.util.PdfSourceRef

@Composable
internal fun KnowledgeDetailPdfButton(
    navController: NavHostController,
    pdfRef: PdfSourceRef.Ref,
    pageNumber: Int? = null,
    bboxJson: String? = null,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = {
            val bboxEncoded = bboxJson?.takeIf { it.isNotBlank() }?.let { Uri.encode(it) }
            navController.navigate(
                Screen.PdfViewer.createRoute(
                    fileId = pdfRef.fileId,
                    name = Uri.encode(pdfRef.fileName),
                    page = pageNumber,
                    bboxEncoded = bboxEncoded
                )
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text("查看 PDF：${pdfRef.fileName}")
    }
}
