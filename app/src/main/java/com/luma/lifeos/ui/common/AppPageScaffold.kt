package com.luma.lifeos.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import com.luma.lifeos.R

enum class PageNavigation { NONE, BACK, CLOSE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPageScaffold(
    title: String,
    navigation: PageNavigation,
    onNavigate: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    navigationEnabled: Boolean = true,
    headerTag: String = "page_header",
    titleTag: String = "page_title",
    navigationTag: String = if (navigation == PageNavigation.CLOSE) "page_close" else "page_back",
    content: @Composable (PaddingValues) -> Unit,
) {
    require((navigation == PageNavigation.NONE) == (onNavigate == null)) {
        "Page navigation requires a callback, and a callback is only valid with navigation."
    }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageHeader(
                title = title,
                navigation = navigation,
                onNavigate = onNavigate,
                navigationEnabled = navigationEnabled,
                actions = actions,
                headerTag = headerTag,
                titleTag = titleTag,
                navigationTag = navigationTag,
            )
        },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageHeader(
    title: String,
    navigation: PageNavigation = PageNavigation.NONE,
    onNavigate: (() -> Unit)? = null,
    navigationEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    headerTag: String = "page_header",
    titleTag: String = "page_title",
    navigationTag: String = if (navigation == PageNavigation.CLOSE) "page_close" else "page_back",
) {
    require((navigation == PageNavigation.NONE) == (onNavigate == null)) {
        "Page navigation requires a callback, and a callback is only valid with navigation."
    }
    val closeDescription = stringResource(R.string.cancel)
    val titleStyle = MaterialTheme.typography.headlineMedium
    // Reserve two scaled title lines plus breathing room. The app scaffold
    // continues to own system insets; the header owns only its content height.
    val titleHeight = with(LocalDensity.current) { titleStyle.lineHeight.toDp() * 2 + 16.dp }
    TopAppBar(
        modifier = modifier.testTag(headerTag),
        expandedHeight = maxOf(TopAppBarDefaults.TopAppBarExpandedHeight, titleHeight),
        title = {
            Text(
                text = title,
                style = titleStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }.testTag(titleTag),
            )
        },
        navigationIcon = {
            when (navigation) {
                PageNavigation.NONE -> Unit
                PageNavigation.BACK -> IconButton(
                    onClick = requireNotNull(onNavigate),
                    enabled = navigationEnabled,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag(navigationTag),
                ) {
                    Icon(painterResource(R.drawable.ic_chevron),
                        contentDescription = stringResource(R.string.back))
                }
                PageNavigation.CLOSE -> IconButton(
                    onClick = requireNotNull(onNavigate),
                    enabled = navigationEnabled,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = closeDescription }
                        .testTag(navigationTag),
                ) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}
