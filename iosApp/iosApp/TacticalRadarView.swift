import SwiftUI

struct TacticalRadarView: View {
    @Binding var isScanning: Bool
    @State private var rotation: Double = 0.0
    @State private var blipOpacity: Double = 0.0
    
    // Simulate detected signals
    let detectedSignals = [
        CGPoint(x: 80, y: -40),
        CGPoint(x: -60, y: 70),
        CGPoint(x: 20, y: -90)
    ]
    
    var body: some View {
        ZStack {
            // Background Radar Grid & Circles
            Group {
                Circle().stroke(Color.green.opacity(0.2), lineWidth: 1)
                Circle().stroke(Color.green.opacity(0.4), lineWidth: 1.5).padding(60)
                Circle().stroke(Color.green.opacity(0.7), lineWidth: 2).padding(120)
                
                // Crosshairs
                Rectangle().fill(Color.green.opacity(0.5)).frame(width: 1, height: 300)
                Rectangle().fill(Color.green.opacity(0.5)).frame(width: 300, height: 1)
            }
            
            // Scanner Sweep (The Fiery Sweep)
            if isScanning {
                Circle()
                    .fill(
                        AngularGradient(
                            gradient: Gradient(colors: [Color.clear, Color.green.opacity(0.1), Color.green.opacity(0.9)]),
                            center: .center,
                            startAngle: .degrees(0),
                            endAngle: .degrees(360)
                        )
                    )
                    .rotationEffect(.degrees(rotation))
                    .onAppear {
                        withAnimation(Animation.linear(duration: 2.0).repeatForever(autoreverses: false)) {
                            rotation = 360.0
                        }
                    }
                    .onDisappear {
                        rotation = 0.0
                    }
                    
                // Simulated Signal Blips appearing dynamically
                ForEach(0..<detectedSignals.count, id: \.self) { index in
                    Circle()
                        .fill(Color.red)
                        .frame(width: 12, height: 12)
                        .shadow(color: .red, radius: 5, x: 0, y: 0)
                        .offset(x: detectedSignals[index].x, y: detectedSignals[index].y)
                        .opacity(blipOpacity)
                        .animation(
                            Animation.easeInOut(duration: 0.5)
                                .repeatForever(autoreverses: true)
                                .delay(Double(index) * 0.4),
                            value: blipOpacity
                        )
                }
                .onAppear {
                    blipOpacity = 1.0
                }
                .onDisappear {
                    blipOpacity = 0.0
                }
            }
            
            // Center Dot (User Location)
            Circle()
                .fill(Color.yellow)
                .frame(width: 12, height: 12)
                .shadow(color: .yellow, radius: 4)
        }
        .frame(width: 300, height: 300)
        .drawingGroup() // Optimize rendering for high performance
    }
}
