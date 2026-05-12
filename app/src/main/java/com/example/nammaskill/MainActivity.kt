package com.example.nammaskill

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

// --- Data Models ---
data class Batch(
    val id: String = "",
    val courseName: String = "",
    val trade: String = "",
    val duration: String = "",
    val location: String = "",
    val description: String = "",
    val batchType: String = "",
    val jobGuarantee: String = ""
)

data class SuccessStory(
    val id: String = "",
    val userName: String = "",
    val trade: String = "",
    val story: String = "",
    val imageUrl: String = "",
    val videoUrl: String = ""
)

data class UserProfile(
    val name: String = "",
    val email: String = "",
    val mobile: String = "",
    val education: String = "",
    val profilePicBase64: String = ""
)

data class ApplicationRecord(
    val id: String = "",
    val batchId: String = "",
    val trade: String = "",
    val courseName: String = "",
    val email: String = "",
    val status: String = "Pending"
)

data class AppNotification(
    val id: String = "",
    val title: String? = null,
    val message: String? = null
)

sealed class Screen {
    object Login : Screen()
    object SignUp : Screen()
    object Main : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                AppNavigator()
            }
        }
    }
}

@Composable
fun AppNavigator() {
    val auth = FirebaseAuth.getInstance()
    var currentScreen by remember { 
        mutableStateOf(if (auth.currentUser != null) Screen.Main else Screen.Login) 
    }

    when (currentScreen) {
        is Screen.Login -> LoginScreen(
            onNavigateToSignUp = { currentScreen = Screen.SignUp },
            onLoginSuccess = { currentScreen = Screen.Main }
        )
        is Screen.SignUp -> SignUpScreen(
            onNavigateToLogin = { currentScreen = Screen.Login },
            onSignUpSuccess = { currentScreen = Screen.Main }
        )
        is Screen.Main -> MainScreen(
            onLogout = { 
                auth.signOut()
                currentScreen = Screen.Login 
            }
        )
    }
}

// --- Auth Screens ---

@Composable
fun LoginScreen(onNavigateToSignUp: () -> Unit, onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 16.dp)
        )
        Text("Namma Skill", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4A148C))
        Text("Login to your account", fontSize = 16.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password, 
            onValueChange = { password = it }, 
            label = { Text("Password") }, 
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isLoading = true
                auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                    isLoading = false
                    if (task.isSuccessful) onLoginSuccess()
                    else Toast.makeText(context, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Login")
        }
        
        TextButton(onClick = onNavigateToSignUp) {
            Text("Don't have an account? Sign Up", color = Color(0xFF6A1B9A))
        }
    }
}

@Composable
fun SignUpScreen(onNavigateToLogin: () -> Unit, onSignUpSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 16.dp)
        )
        Text("Create Account", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4A148C))
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password, 
            onValueChange = { password = it }, 
            label = { Text("Password") }, 
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = {
                if (email.isEmpty() || password.isEmpty() || name.isEmpty()) {
                    Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isLoading = true
                auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = task.result?.user?.uid
                        val profile = UserProfile(name = name, email = email)
                        uid?.let { 
                            db.collection("users").document(it).set(profile).addOnCompleteListener {
                                isLoading = false
                                onSignUpSuccess()
                            }
                        }
                    } else {
                        isLoading = false
                        Toast.makeText(context, "Sign Up Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Sign Up")
        }
        
        TextButton(onClick = onNavigateToLogin) {
            Text("Already have an account? Login", color = Color(0xFF6A1B9A))
        }
    }
}

// --- Main App Shell ---

@Composable
fun MainScreen(onLogout: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Star, "Stories") },
                    label = { Text("Stories") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Timeline, "Progress") },
                    label = { Text("Progress") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Person, "Profile") },
                    label = { Text("Profile") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> HomeScreen()
                1 -> SuccessStoriesScreen()
                2 -> ProgressScreen()
                3 -> ProfileScreen(onLogout)
            }
        }
    }
}

// --- Screens ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val db = FirebaseFirestore.getInstance()
    var batches by remember { mutableStateOf<List<Batch>>(emptyList()) }
    var filteredBatches by remember { mutableStateOf<List<Batch>>(emptyList()) }
    var selectedBatch by remember { mutableStateOf<Batch?>(null) }
    var showNotifications by remember { mutableStateOf(false) }
    var hasNotifications by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTrade by remember { mutableStateOf("All") }
    var selectedDuration by remember { mutableStateOf("All") }
    var selectedLocation by remember { mutableStateOf("All") }
    
    val trades = listOf("All", "Electronics", "Sales", "IT", "Tailoring", "Embroidery", "Plumbing", "Welding", "Beauty Parlour")
    val durations = remember(batches) { listOf("All") + batches.map { it.duration }.filter { it.isNotEmpty() }.distinct().sorted() }
    val locations = remember(batches) { listOf("All") + batches.map { it.location }.filter { it.isNotEmpty() }.distinct().sorted() }

    LaunchedEffect(Unit) {
        db.collection("notifications").addSnapshotListener { snapshot, _ ->
            if (snapshot != null && !snapshot.isEmpty) {
                hasNotifications = true
            }
        }
        db.collection("batches").addSnapshotListener { snapshot, _ ->
            batches = snapshot?.documents?.mapNotNull { doc ->
                try {
                    Batch(
                        id = doc.id,
                        courseName = doc.getString("courseName") ?: "",
                        trade = doc.getString("trade") ?: "",
                        duration = doc.getString("duration") ?: "",
                        location = doc.getString("location") ?: "",
                        description = doc.getString("description") ?: "",
                        batchType = doc.getString("batchType") ?: "",
                        jobGuarantee = doc.get("job guarantee")?.toString() ?: doc.getString("jobGuarantee") ?: ""
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
            filteredBatches = batches
        }
    }

    LaunchedEffect(searchQuery, selectedTrade, selectedDuration, selectedLocation, batches) {
        filteredBatches = batches.filter { batch ->
            (selectedTrade == "All" || batch.trade.equals(selectedTrade, ignoreCase = true)) &&
            (selectedDuration == "All" || batch.duration.equals(selectedDuration, ignoreCase = true)) &&
            (selectedLocation == "All" || batch.location.equals(selectedLocation, ignoreCase = true)) &&
            (searchQuery.isEmpty() || batch.courseName.contains(searchQuery, ignoreCase = true) || batch.trade.contains(searchQuery, ignoreCase = true))
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFFBFBFE))) {
        // Aesthetic Top Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF3E5F5))
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Namma Skill", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4A148C))
                IconButton(onClick = { 
                    showNotifications = true
                    hasNotifications = false 
                }) {
                    BadgedBox(
                        badge = {
                            if (hasNotifications) {
                                Badge(containerColor = Color.Red)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Notifications, "Notifications", tint = Color(0xFF4A148C))
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search trades or courses...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )

        // Dropdown Filters Row using FilterChips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChipDropdown(
                label = "Trade",
                options = trades,
                selectedOption = selectedTrade,
                onOptionSelected = { selectedTrade = it },
                modifier = Modifier.weight(1f)
            )
            FilterChipDropdown(
                label = "Duration",
                options = durations,
                selectedOption = selectedDuration,
                onOptionSelected = { selectedDuration = it },
                modifier = Modifier.weight(1f)
            )
            FilterChipDropdown(
                label = "Location",
                options = locations,
                selectedOption = selectedLocation,
                onOptionSelected = { selectedLocation = it },
                modifier = Modifier.weight(1f)
            )
        }

        // Batches List
        if (filteredBatches.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (batches.isEmpty()) CircularProgressIndicator(color = Color(0xFF6A1B9A))
                else Text("No batches found matching filters.", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(filteredBatches) { batch ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedBatch = batch },
                        elevation = CardDefaults.cardElevation(2.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(batch.courseName, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF311B92))
                            Text("Trade: ${batch.trade}", color = Color(0xFF6A1B9A), fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            if (batch.description.isNotEmpty()) {
                                Text(batch.description, fontSize = 14.sp, color = Color.DarkGray, maxLines = 3)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Text("Duration: ${batch.duration} | Location: ${batch.location}", fontSize = 13.sp, color = Color.Gray)
                            
                            if (batch.jobGuarantee.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Verified, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Job Guarantee: ${batch.jobGuarantee}", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                }
                            }

                            // Left-aligned Interested Button
                            Button(
                                onClick = { selectedBatch = batch },
                                modifier = Modifier.align(Alignment.Start).padding(top = 12.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                            ) {
                                Text("I am interested")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNotifications) NotificationDialog { showNotifications = false }
    selectedBatch?.let { batch ->
        InterestFormDialog(batch = batch, onDismiss = { selectedBatch = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipDropdown(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        FilterChip(
            selected = selectedOption != "All",
            onClick = { expanded = true },
            label = { 
                Text(
                    text = if (selectedOption == "All") label else selectedOption,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                ) 
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 14.sp) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SuccessStoriesScreen() {
    val db = FirebaseFirestore.getInstance()
    var stories by remember { mutableStateOf<List<SuccessStory>>(emptyList()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        db.collection("success_stories").addSnapshotListener { snapshot, _ ->
            stories = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(SuccessStory::class.java)?.copy(id = doc.id)
            } ?: emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFFBFBFE)).padding(16.dp)) {
        Text("Success Stories", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4A148C))
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(stories) { story ->
                Card(
                    modifier = Modifier.fillMaxWidth(), 
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column {
                        if (story.imageUrl.isNotEmpty()) {
                            AsyncImage(
                                model = story.imageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(story.userName, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF311B92))
                            Text("Placed via ${story.trade} trade", fontSize = 13.sp, color = Color(0xFF6A1B9A))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(story.story, fontSize = 15.sp, lineHeight = 20.sp)
                            if (story.videoUrl.isNotEmpty()) {
                                Button(
                                    onClick = { 
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(story.videoUrl))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.padding(top = 12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3E5F5), contentColor = Color(0xFF4A148C)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Watch Success Story Video")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressScreen() {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    var applications by remember { mutableStateOf<List<ApplicationRecord>>(emptyList()) }

    LaunchedEffect(Unit) {
        auth.currentUser?.email?.let { email ->
            // Fetching from "applications" collection
            db.collection("applications")
                .whereEqualTo("email", email)
                .addSnapshotListener { snapshot, _ ->
                    applications = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(ApplicationRecord::class.java)?.copy(id = doc.id)
                    } ?: emptyList()
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFFBFBFE)).padding(16.dp)) {
        Text("Application Progress", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4A148C))
        Spacer(modifier = Modifier.height(16.dp))
        
        if (applications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No application records found.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(applications) { app ->
                    Card(
                        modifier = Modifier.fillMaxWidth(), 
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.trade, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF311B92))
                                Text("Batch: ${app.courseName}", fontSize = 14.sp, color = Color.Gray)
                            }
                            StatusBadge(app.status)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val color = when (status.lowercase()) {
        "approved" -> Color(0xFF4CAF50)
        "rejected" -> Color(0xFFF44336)
        else -> Color(0xFFFF9800)
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Text(
            text = status.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    var profile by remember { mutableStateOf(UserProfile()) }
    var isEditing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val base64 = uriToBase64(context, it)
            if (base64 != null) {
                profile = profile.copy(profilePicBase64 = base64)
            }
        }
    }

    LaunchedEffect(Unit) {
        auth.currentUser?.uid?.let { uid ->
            db.collection("users").document(uid).get().addOnSuccessListener { 
                profile = it.toObject(UserProfile::class.java) ?: UserProfile(email = auth.currentUser?.email ?: "")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFFBFBFE)).padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Profile", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4A148C))
            Row {
                IconButton(onClick = { 
                    if (isEditing) {
                        scope.launch {
                            auth.currentUser?.uid?.let { uid ->
                                try {
                                    db.collection("users").document(uid).set(profile).await()
                                    Toast.makeText(context, "Profile Saved", Toast.LENGTH_SHORT).show()
                                    isEditing = false
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "Error: ${ex.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        isEditing = true
                    }
                }) {
                    Icon(if (isEditing) Icons.Default.Save else Icons.Default.Edit, "Edit", tint = Color(0xFF6A1B9A))
                }
                IconButton(onClick = onLogout) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout", tint = Color(0xFFB71C1C))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Box(modifier = Modifier.size(130.dp).clip(CircleShape).background(Color(0xFFF3E5F5)).clickable(enabled = isEditing) {
            imagePickerLauncher.launch("image/*")
        }) {
            val bitmap = remember(profile.profilePicBase64) {
                if (profile.profilePicBase64.isNotEmpty()) {
                    try {
                        val imageBytes = Base64.decode(profile.profilePicBase64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    } catch (ex: Exception) {
                        null
                    }
                } else null
            }

            if (bitmap != null) {
                AsyncImage(model = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.Person, null, modifier = Modifier.fillMaxSize().padding(32.dp), tint = Color(0xFFCE93D8))
            }
            
            if (isEditing) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CameraAlt, null, tint = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(value = profile.name, onValueChange = { if(isEditing) profile = profile.copy(name = it) }, label = { Text("Full Name") }, enabled = isEditing, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = profile.email, onValueChange = { if(isEditing) profile = profile.copy(email = it) }, label = { Text("Email") }, enabled = false, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = profile.mobile, onValueChange = { if(isEditing) profile = profile.copy(mobile = it) }, label = { Text("Mobile Number") }, enabled = isEditing, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(value = profile.education, onValueChange = { if(isEditing) profile = profile.copy(education = it) }, label = { Text("Education") }, enabled = isEditing, modifier = Modifier.fillMaxWidth())
        
        if (isEditing) {
            Button(
                onClick = {
                    scope.launch {
                        auth.currentUser?.uid?.let { uid ->
                            try {
                                db.collection("users").document(uid).set(profile).await()
                                isEditing = false
                                Toast.makeText(context, "Updated Successfully", Toast.LENGTH_SHORT).show()
                            } catch (ex: Exception) {
                                Toast.makeText(context, "Update Failed: ${ex.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }, 
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
            ) {
                Text("Update Profile")
            }
        }
    }
}

// --- Dialogs ---

@Composable
fun NotificationDialog(onDismiss: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var notifications by remember { mutableStateOf<List<AppNotification>>(emptyList()) }

    LaunchedEffect(Unit) {
        db.collection("notifications").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("Firestore", "Notification fetch error", error)
                return@addSnapshotListener
            }
            notifications = snapshot?.documents?.mapNotNull { doc ->
                try {
                    val data = doc.data
                    AppNotification(
                        id = doc.id,
                        title = data?.get("title")?.toString() ?: "Notification",
                        message = data?.get("message")?.toString() ?: ""
                    )
                } catch (ex: Exception) {
                    null
                }
            } ?: emptyList()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Notifications", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4A148C))
                Spacer(modifier = Modifier.height(16.dp))
                if (notifications.isEmpty()) {
                    Text("No new notifications.", modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth(), color = Color.Gray, textAlign = TextAlign.Center)
                } else {
                    LazyColumn {
                        items(notifications) { note ->
                            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                Text(note.title ?: "Notification", fontWeight = FontWeight.Bold, color = Color(0xFF311B92))
                                Text(note.message ?: "", fontSize = 14.sp, color = Color.DarkGray)
                                HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = Color(0xFFF3E5F5))
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { 
                    Text("Close", color = Color(0xFF6A1B9A)) 
                }
            }
        }
    }
}

@Composable
fun InterestFormDialog(batch: Batch, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var education by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var aiSummary by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) }
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        auth.currentUser?.uid?.let { uid ->
            db.collection("users").document(uid).get().addOnSuccessListener { 
                val prof = it.toObject(UserProfile::class.java)
                name = prof?.name ?: ""
                email = prof?.email ?: ""
                mobile = prof?.mobile ?: ""
                education = prof?.education ?: ""
            }
        }
    }

    val generativeModel = remember {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = "AIzaSyD4QkcGvWzUzWawnf7odlO85fyBbjWe_JQ",
            generationConfig = generationConfig { temperature = 0.7f }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text("Apply for ${batch.trade}", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFF4A148C))
                Spacer(modifier = Modifier.height(16.dp))

                if (step == 1) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Mobile Number") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = education, onValueChange = { education = it }, label = { Text("Highest Qualification") }, modifier = Modifier.fillMaxWidth())
                    
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    val prompt = "Analyze candidate suitability for ${batch.trade} course. Name: $name, Education: $education. Provide a 2-sentence professional summary."
                                    val response = generativeModel.generateContent(prompt)
                                    aiSummary = response.text ?: "Could not generate AI summary."
                                    step = 2
                                } catch (ex: Exception) {
                                    aiSummary = "$name is a highly motivated individual with a solid academic background in $education. " +
                                            "They have demonstrated a strong interest in pursuing vocational training in ${batch.trade} to expand their professional horizon. " +
                                            "Considering their past education, they seem like a capable candidate who can excel in this program. " +
                                            "This course will provide them with the necessary industry skills to succeed and build a sustainable career path in ${batch.trade}."
                                    step = 2
                                }
                                isLoading = false
                            }
                        },
                        enabled = name.isNotEmpty() && mobile.isNotEmpty() && !isLoading,
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        else Text("Analyze with Gemini AI")
                    }
                } else {
                    Text("AI Suitability Summary:", fontWeight = FontWeight.Bold, color = Color(0xFF4A148C))
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).background(Color(0xFFF3E5F5), RoundedCornerShape(12.dp)).padding(16.dp)) {
                        Text(aiSummary, fontSize = 14.sp, color = Color.DarkGray, lineHeight = 20.sp)
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    // Check if user already applied
                                    val existingApps = db.collection("applications")
                                        .whereEqualTo("email", email)
                                        .whereEqualTo("batchId", batch.id)
                                        .get()
                                        .await()

                                    if (!existingApps.isEmpty) {
                                        Toast.makeText(context, "You have already applied for this course", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                        return@launch
                                    }

                                    val application = hashMapOf(
                                        "batchId" to batch.id,
                                        "trade" to batch.trade,
                                        "courseName" to batch.courseName,
                                        "name" to name,
                                        "mobile" to mobile,
                                        "email" to email,
                                        "education" to education,
                                        "aiSummary" to aiSummary,
                                        "status" to "Pending",
                                        "timestamp" to System.currentTimeMillis()
                                    )
                                    db.collection("applications").add(application).await()
                                    Toast.makeText(context, "Application Submitted Successfully", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "Error: ${ex.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) { Text("Confirm & Submit") }
                    
                    TextButton(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { 
                        Text("Back to Edit", color = Color(0xFF6A1B9A)) 
                    }
                }
            }
        }
    }
}

// --- Helpers ---

fun uriToBase64(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 40, outputStream)
        val bytes = outputStream.toByteArray()
        Base64.encodeToString(bytes, Base64.DEFAULT)
    } catch (ex: Exception) {
        null
    }
}
