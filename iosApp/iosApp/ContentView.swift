import SwiftUI
import shared // KMP bridge for accessing AI logic like DeviceType from Android

struct ContentView: View {
    @State private var isScanning = false
    
    var body: some View {
        ZStack {
            Color.black.edgesIgnoringSafeArea(.all)
            
            VStack {
                Text("EXAM SHIELD AI")
                    .font(.custom("Courier New", size: 24))
                    .fontWeight(.bold)
                    .foregroundColor(.green)
                    .padding(.top, 40)
                
                // ASTRA NEXUS: Integration of Shared KMP Logic
                let advisor = AIPerformanceAdvisor()
                Text("NEURAL LINK: \(advisor.getDiagnosticChatResponse(query: "status"))")
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundColor(.green.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)

                Text(isScanning ? "SYSTEM ACTIVE - AGENTS DEPLOYED" : "SYSTEM STANDBY")
                    .font(.caption)
                    .foregroundColor(isScanning ? .red : .gray)
                    .padding(.top, 5)
                    .padding(.bottom, 20)
                
                // Native iOS Radar UI
                TacticalRadarView(isScanning: $isScanning)
                    .frame(width: 300, height: 300)
                    .padding()
                
                Spacer()
                
                Button(action: {
                    withAnimation {
                        isScanning.toggle()
                    }
                }) {
                    Text(isScanning ? "DISENGAGE SCANNER" : "INITIALIZE SCANNER")
                        .font(.headline)
                        .foregroundColor(.black)
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(isScanning ? Color.red : Color.green)
                        .cornerRadius(10)
                }
                .padding(.horizontal, 40)
                .padding(.bottom, 50)
            }
        }
    }
}
