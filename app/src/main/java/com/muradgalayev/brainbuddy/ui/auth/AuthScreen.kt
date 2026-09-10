package com.muradgalayev.brainbuddy.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.muradgalayev.brainbuddy.R
import kotlinx.coroutines.delay

@Composable
private fun AuthBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFF9F6F4),
                        Color(0xFFF5F2F1),
                        Color(0xFFFAF8F6)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFD08A),
                        Color(0xFFFFB45C),
                        Color(0xFFFFA24A)
                    ),
                    center = Offset(w * 1.18f, -w * 0.12f),
                    radius = w * 0.52f
                ),
                radius = w * 0.52f,
                center = Offset(w * 1.18f, -w * 0.12f)
            )

            // soft light area near the blob edge
            drawCircle(
                color = Color.White.copy(alpha = 0.18f),
                radius = w * 0.43f,
                center = Offset(w * 0.98f, -w * 0.02f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.4.dp.toPx()
                )
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = w * 0.52f,
                center = Offset(w * 0.98f, -w * 0.02f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.dp.toPx()
                )
            )
        }
    }
}
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }

    val view = androidx.compose.ui.platform.LocalView.current

    LaunchedEffect(Unit) {
        val window = (view.context as android.app.Activity).window
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        androidx.core.view.WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = true
        androidx.core.view.WindowInsetsControllerCompat(window, view).isAppearanceLightNavigationBars = true
    }
    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) onAuthSuccess()
    }

    // reset the loading state if the user backs out of the Google Custom Tab without finishing.
    // fires each time we come back to the foreground
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onScreenResumed()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AuthBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(96.dp))

            Icon(
                painter = painterResource(id = R.drawable.ic_ai),
                contentDescription = "Myndora",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFF202230))) {
                        append("Brain")
                    }
                    withStyle(SpanStyle(color = Color(0xFFFF6A1A))) {
                        append("Buddy")
                    }
                },
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (state.isLoginMode) "Welcome back" else "Create your account",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // sign-up only fields
            AnimatedVisibility(
                visible = !state.isLoginMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        SoftPillTextField(
                            value = state.firstName,
                            onValueChange = viewModel::onFirstNameChanged,
                            placeholder = "First name",
                            leadingIcon = Icons.Outlined.Person,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Right) }
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        SoftPillTextField(
                            value = state.lastName,
                            onValueChange = viewModel::onLastNameChanged,
                            placeholder = "Last name",
                            leadingIcon = Icons.Outlined.Person,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    SoftPillTextField(
                        value = state.phone,
                        onValueChange = viewModel::onPhoneChanged,
                        placeholder = "Phone number",
                        leadingIcon = Icons.Outlined.Phone,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            SoftPillTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChanged,
                placeholder = "Email",
                leadingIcon = Icons.Outlined.Email,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            SoftPillTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChanged,
                placeholder = "Password",
                leadingIcon = Icons.Outlined.Lock,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { passwordVisible = !passwordVisible }
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        viewModel.submit()
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (state.isLoginMode) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "Forgot password?",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { viewModel.openForgotPassword() }
                            .padding(4.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = state.error != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                AuthBanner(
                    text = state.error ?: "",
                    background = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            AnimatedVisibility(
                visible = state.infoMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                AuthBanner(
                    text = state.infoMessage ?: "",
                    background = Color(0xFFE9F5EC),
                    contentColor = Color(0xFF1F5B2E)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            GradientSignInButton(
                label = if (state.isLoginMode) "Sign In" else "Sign Up",
                isLoading = state.isLoading,
                onClick = viewModel::submit
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = "  or  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            GoogleSignInButton(
                enabled = !state.isLoading,
                onClick = viewModel::signInWithGoogle
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Text(
                    text = if (state.isLoginMode) "Don't have an account? " else "Already have an account? ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (state.isLoginMode) "Sign Up" else "Sign In",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { viewModel.toggleMode() }
                )
            }
        }
    }

    if (state.forgotPasswordOpen) {
        ForgotPasswordDialog(
            email = state.forgotPasswordEmail,
            sending = state.forgotPasswordSending,
            error = state.forgotPasswordError,
            onEmailChanged = viewModel::onForgotEmailChanged,
            onSend = viewModel::sendPasswordReset,
            onDismiss = viewModel::dismissForgotPassword,
        )
    }

    if (state.newPasswordOpen) {
        NewPasswordDialog(
            saving = state.newPasswordSaving,
            error = state.newPasswordError,
            onSubmit = viewModel::submitNewPassword,
        )
    }
}
@Composable
private fun AuthBanner(
    text: String,
    background: Color,
    contentColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SoftPillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.78f))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = Color(0xFF9E9BA3),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = Color(0xFFA5A1A8),
                    fontSize = 16.sp
                )
            }

            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color(0xFF1C1917),
                    fontSize = 16.sp
                ),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailingIcon()
        }
    }
}

@Composable
private fun GradientSignInButton(
    label: String,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFFB22C),
                        Color(0xFFFF7A2F),
                        Color(0xFFFF5A36)
                    )
                )
            )
            .clickable(enabled = !isLoading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(
                text = label,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFFFF6A30),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun GoogleSignInButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(260)
        expanded = true
        delay(170)
        showText = true
    }

    val buttonWidth by animateDpAsState(
        targetValue = if (expanded) 320.dp else 56.dp,
        animationSpec = spring(
            dampingRatio = .92f,
            stiffness = Spring.StiffnessLow,
        ),
        label = "googleButtonWidth"
    )
    val horizontalPadding by animateDpAsState(
        targetValue = if (expanded) 20.dp else 0.dp,
        animationSpec = spring(dampingRatio = .95f, stiffness = Spring.StiffnessLow),
        label = "googleButtonPadding",
    )

    Row(
        modifier = Modifier
            .width(buttonWidth)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .border(
                width = 1.dp,
                color = Color(0xFFE4E1E1),
                shape = RoundedCornerShape(28.dp)
            )
            .clickable(enabled = enabled && expanded) { onClick() }
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_google),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(24.dp)
        )

        AnimatedVisibility(
            visible = showText,
            enter = fadeIn(animationSpec = tween(360)) +
                expandHorizontally(
                    expandFrom = Alignment.Start,
                    animationSpec = spring(dampingRatio = .94f, stiffness = Spring.StiffnessLow),
                ) +
                slideInHorizontally(
                    initialOffsetX = { it / 5 },
                    animationSpec = tween(380),
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Continue with Google",
                    color = Color(0xFF202230),
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun ForgotPasswordDialog(
    email: String,
    sending: Boolean,
    error: String?,
    onEmailChanged: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Reset password",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter the email you signed up with. We'll send you a link to set a new password.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChanged,
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                    singleLine = true,
                    enabled = !sending,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSend() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSend,
                enabled = !sending && email.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text("Send link", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending) { Text("Cancel") }
        }
    )
}

@Composable
private fun NewPasswordDialog(
    saving: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val mismatch = confirm.isNotEmpty() && password != confirm

    // no dismiss button: Supabase already put the account into recovery mode via the link, and if
    // the user backs out their session is still authenticated with an unchanged password. the
    // onDismissRequest is a no-op so the OS back button doesn't drop the dialog silently
    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Set a new password",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Choose a new password to finish signing in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("New password") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    trailingIcon = {
                        Icon(
                            imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = null,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable { visible = !visible }
                        )
                    },
                    singleLine = true,
                    enabled = !saving,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm password") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    singleLine = true,
                    enabled = !saving,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (!mismatch) onSubmit(password) }
                    ),
                    isError = mismatch,
                    modifier = Modifier.fillMaxWidth()
                )
                val bottomError = error ?: if (mismatch) "Passwords don't match" else null
                if (bottomError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = bottomError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(password) },
                enabled = !saving && password.isNotBlank() && !mismatch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text("Save & sign in", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )
}
