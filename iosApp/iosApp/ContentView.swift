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
                
                Text(isScanning ? "SYSTEM ACTIVE - AGENTS DEPLOYED" : "SYSTEM STANDBY")
                    .font(.caption)
                    .foregroundColor(isScanning ? .red : .gray)
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
